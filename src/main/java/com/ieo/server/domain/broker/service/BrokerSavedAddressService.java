package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.SaveBrokerAddressRequest;
import com.ieo.server.domain.broker.dto.SavedBrokerAddressResponse;
import com.ieo.server.domain.broker.entity.BrokerSavedAddress;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.broker.repository.BrokerSavedAddressRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BrokerSavedAddressService {
    private final BrokerService brokerService;
    private final BrokerRepository brokerRepository;
    private final BrokerSavedAddressRepository repository;

    @Transactional(readOnly = true)
    public List<SavedBrokerAddressResponse> list(Long userId) {
        Long brokerId = brokerService.getMine(userId).id();
        return repository.findAllByBrokerIdOrderByIdDesc(brokerId).stream()
                .map(SavedBrokerAddressResponse::from).toList();
    }

    public SavedBrokerAddressResponse save(Long userId, SaveBrokerAddressRequest request) {
        Long brokerId = brokerService.getMine(userId).id();
        BrokerSavedAddress saved = BrokerSavedAddress.create(brokerRepository.getReferenceById(brokerId), request);
        if (repository.existsByBrokerIdAndAddressAndDetailAddress(brokerId, saved.getAddress(), saved.getDetailAddress())) {
            throw new BusinessException(ErrorCode.SAVED_ADDRESS_DUPLICATE);
        }
        try {
            return SavedBrokerAddressResponse.from(repository.saveAndFlush(saved));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SAVED_ADDRESS_DUPLICATE);
        }
    }

    public void delete(Long userId, Long id) {
        Long brokerId = brokerService.getMine(userId).id();
        BrokerSavedAddress saved = repository.findByIdAndBrokerId(id, brokerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SAVED_ADDRESS_NOT_FOUND));
        repository.delete(saved);
    }
}
