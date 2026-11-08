package com.ieo.server.domain.broker.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.auth.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerSavedAddressControllerTest {
    private static final String PATH = "/api/brokers/me/saved-addresses";
    private static final String BODY = """
            {"name":"  평택 물류센터  ","address":"  경기 평택시 평택항로 1  ",
             "detailAddress":"  2동 3번 게이트  ","lat":36.9631,"lng":126.8337}
            """;
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private BrokerMemberRepository members;
    @Autowired private BrokerService brokers;
    @Autowired private JwtProvider jwt;
    private MockMvc mvc;
    private User owner;
    private String bearer;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        owner = users.save(User.create("01090008001", UserRole.BROKER));
        brokers.register(owner.getId(), new CreateBrokerRequest("주소 테스트", "1234567891", "제2026-001호", null));
        bearer = token(owner);
    }

    @Test
    void 주소와_상세주소_좌표를_저장하고_목록에서_불러온다() throws Exception {
        save(bearer, BODY).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("평택 물류센터"))
                .andExpect(jsonPath("$.address").value("경기 평택시 평택항로 1"))
                .andExpect(jsonPath("$.detailAddress").value("2동 3번 게이트"))
                .andExpect(jsonPath("$.lat").value(36.9631))
                .andExpect(jsonPath("$.lng").value(126.8337));
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].detailAddress").value("2동 3번 게이트"));
    }

    @Test
    void 같은_주소와_상세주소를_중복으로_저장하지_않는다() throws Exception {
        save(bearer, BODY).andExpect(status().isCreated());
        save(bearer, BODY).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAVED_ADDRESS_409"));
        save(bearer, BODY.replace("2동 3번 게이트", "1동" )).andExpect(status().isCreated());
    }

    @Test
    void 삭제하면_다음_목록에서_빠진다() throws Exception {
        long id = savedId();
        mvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isNotFound());
    }

    @Test
    void 다른_회사의_주소를_조회하거나_삭제할_수_없다() throws Exception {
        long id = savedId();
        User rival = users.save(User.create("01090008002", UserRole.BROKER));
        brokers.register(rival.getId(), new CreateBrokerRequest("다른 회사", "9876543215", "제2026-002호", null));
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token(rival)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete(PATH + "/" + id).header(HttpHeaders.AUTHORIZATION, token(rival)))
                .andExpect(status().isNotFound());
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 같은_회사의_직원도_주소를_공유하고_저장할_수_있다() throws Exception {
        savedId();
        User staff = users.save(User.create("01090008003", UserRole.BROKER));
        members.save(BrokerMember.of(staff, members.findById(owner.getId()).orElseThrow().getBroker(), BrokerMemberPosition.STAFF));
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token(staff))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        save(token(staff), BODY.replace("2동 3번 게이트", "직원 창고")).andExpect(status().isCreated());
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 로그인하지_않거나_회사에서_나온_계정은_접근할_수_없다() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        members.findById(owner.getId()).orElseThrow().remove(Instant.now());
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isForbidden());
    }

    @Test
    void 잘못된_좌표와_이름_길이_주소길이를_검증한다() throws Exception {
        save(bearer, BODY.replace("36.9631", "91")).andExpect(status().isBadRequest());
        save(bearer, BODY.replace("126.8337", "181")).andExpect(status().isBadRequest());
        save(bearer, BODY.replace("평택 물류센터", " ")).andExpect(status().isBadRequest());
        save(bearer, BODY.replace("평택 물류센터", "가".repeat(101))).andExpect(status().isBadRequest());
        save(bearer, BODY.replace("경기 평택시 평택항로 1", "가".repeat(195))).andExpect(status().isBadRequest());
    }

    @Test
    void 상세주소가_없어도_저장할_수_있다() throws Exception {
        save(bearer, BODY.replace("\"detailAddress\":\"  2동 3번 게이트  \",", ""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.detailAddress").value(""));
    }

    private long savedId() throws Exception {
        String json = save(bearer, BODY).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private String token(User user) { return "Bearer " + jwt.issue(user.getId()).accessToken(); }
    private org.springframework.test.web.servlet.ResultActions save(String token, String body) throws Exception {
        return mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}