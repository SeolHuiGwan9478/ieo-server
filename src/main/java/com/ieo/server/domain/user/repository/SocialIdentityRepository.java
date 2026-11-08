package com.ieo.server.domain.user.repository;

import com.ieo.server.domain.user.entity.SocialIdentity;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, Long> {

	Optional<SocialIdentity> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	boolean existsByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	long deleteByUser(User user);
}
