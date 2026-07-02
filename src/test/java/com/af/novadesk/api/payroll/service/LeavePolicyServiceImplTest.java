package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.repository.CmEmployeeEntityAssignmentRepository;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.payroll.constants.LeavePaymentType;
import com.af.novadesk.api.payroll.dto.LeavePolicyRequest;
import com.af.novadesk.api.payroll.entity.LeavePolicy;
import com.af.novadesk.api.payroll.exception.LeavePolicyDuplicateException;
import com.af.novadesk.api.payroll.repository.LeaveBalanceRepository;
import com.af.novadesk.api.payroll.repository.LeavePolicyRepository;
import com.af.novadesk.api.payroll.service.impl.LeavePolicyServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeavePolicyServiceImplTest {

    @Mock private LeavePolicyRepository leavePolicyRepository;
    @Mock private LeaveBalanceRepository leaveBalanceRepository;
    @Mock private CmEmployeeRepository cmEmployeeRepository;
    @Mock private LegalEntityRepository legalEntityRepository;
    @Mock private FiscalYearSettingRepository fiscalYearSettingRepository;
    @Mock private CmEmployeeEntityAssignmentRepository cmAssignmentRepository;

    @InjectMocks
    private LeavePolicyServiceImpl service;

    @Nested
    @DisplayName("createPolicy — duplicate check")
    class CreatePolicyDuplicateCheck {

        @Test
        @DisplayName("recreating a soft-deleted policy succeeds — ACTIVE-only uniqueness check")
        void recreateSoftDeletedPolicy_doesNotThrowDuplicate() {
            UUID entityId = UUID.randomUUID();
            UUID policyId = UUID.randomUUID();

            LegalEntity entity = mock(LegalEntity.class);
            when(entity.getId()).thenReturn(entityId);

            LeavePolicyRequest request = buildRequest(entityId, "Annual Leave");

            LeavePolicy savedPolicy = LeavePolicy.builder()
                    .id(policyId)
                    .legalEntity(entity)
                    .name("Annual Leave")
                    .paymentType(LeavePaymentType.PAID)
                    .allowedDays(20)
                    .isUnlimited(false)
                    .isEarned(false)
                    .createdBy(entityId)
                    .status(Status.ACTIVE)
                    .build();

            when(legalEntityRepository.findById(entityId)).thenReturn(Optional.of(entity));
            // No ACTIVE policy with that name exists — the old one was soft-deleted
            when(leavePolicyRepository.findByLegalEntityIdAndNameAndStatus(
                    entityId, "Annual Leave", Status.ACTIVE))
                    .thenReturn(Optional.empty());
            when(leavePolicyRepository.save(any(LeavePolicy.class))).thenReturn(savedPolicy);
            when(leavePolicyRepository.findById(policyId)).thenReturn(Optional.of(savedPolicy));
            when(cmEmployeeRepository.findAllByLegalEntityId(entityId)).thenReturn(List.of());
            when(fiscalYearSettingRepository.findByLegalEntityId(entityId)).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() -> service.createPolicy(request));

            verify(leavePolicyRepository).findByLegalEntityIdAndNameAndStatus(
                    entityId, "Annual Leave", Status.ACTIVE);
        }

        @Test
        @DisplayName("creating a policy when an ACTIVE policy with the same name exists throws LeavePolicyDuplicateException")
        void createWithExistingActiveName_throwsDuplicateException() {
            UUID entityId = UUID.randomUUID();

            LegalEntity entity = mock(LegalEntity.class);
            LeavePolicy existing = mock(LeavePolicy.class);

            LeavePolicyRequest request = buildRequest(entityId, "Annual Leave");

            when(legalEntityRepository.findById(entityId)).thenReturn(Optional.of(entity));
            when(leavePolicyRepository.findByLegalEntityIdAndNameAndStatus(
                    entityId, "Annual Leave", Status.ACTIVE))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.createPolicy(request))
                    .isInstanceOf(LeavePolicyDuplicateException.class);
        }
    }

    private LeavePolicyRequest buildRequest(UUID entityId, String name) {
        return LeavePolicyRequest.builder()
                .legalEntityId(entityId)
                .name(name)
                .paymentType(LeavePaymentType.PAID)
                .allowedDays(20)
                .isUnlimited(false)
                .isEarned(false)
                .build();
    }
}
