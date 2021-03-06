package com.poccofinance.loan.service;

import com.poccofinance.loan.Loan;
import com.poccofinance.loan.TypeSafeConfig;
import com.poccofinance.loan.converters.LoanConverter;
import com.poccofinance.loan.dto.LoanApplianceDTO;
import com.poccofinance.loan.dto.LoanApplianceResultDTO;
import com.poccofinance.loan.dto.LoanExtensionDTO;
import com.poccofinance.loan.dto.LoanExtensionResultDTO;
import com.poccofinance.loan.exception.InvalidInputException;
import com.poccofinance.loan.exception.ResourceNotFoundException;
import com.poccofinance.loan.repository.LoanRepository;
import com.poccofinance.loan.repository.ResourceUpdateStrategy;
import org.joda.time.LocalDateTime;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

/**
 * LoanService implementation with static values configured such as: fixedExtendTermDays, fixedLoanPrincipal
 */
@Service
public class StaticConfigLoanService implements LoanService {

    private final LoanRepository loanRepository;
    private final LoanConverter loanConverter;
    private final Validator validator;
    private final TypeSafeConfig config;
    private final ResourceUpdateStrategy<Loan> resourceUpdateStrategy;

    public StaticConfigLoanService(
        LoanRepository loanRepository,
        LoanConverter loanConverter,
        Validator validator,
        TypeSafeConfig config,
        ResourceUpdateStrategy<Loan> resourceUpdateStrategy) {

        this.config = config;
        this.loanRepository = loanRepository;
        this.loanConverter = loanConverter;
        this.validator = validator;
        this.resourceUpdateStrategy = resourceUpdateStrategy;
    }

    @Override
    public LoanApplianceResultDTO applyForLoan(LoanApplianceDTO loanApplianceDTO) {
        checkConstraints(loanApplianceDTO);
        final Loan.LoanBuilder loan = loanConverter.convert(loanApplianceDTO)
            .loanId(UUID.randomUUID())
            .principal(config.getPrincipal())
            .dueDate(LocalDateTime.now().plusDays(loanApplianceDTO.getTerm()));

        final Loan savedLoan = loanRepository.save(loan.build());
        return loanConverter.convertLoan(savedLoan);
    }

    @Override
    public LoanExtensionResultDTO extendLoan(LoanExtensionDTO loanExtensionDTO) {
        checkConstraints(loanExtensionDTO);
        final Loan latestLoan = loanRepository.findFirstByLoanIdOrderByRequestedDateDesc(loanExtensionDTO.getLoanId())
            .orElseThrow(() -> new ResourceNotFoundException(Loan.class, loanExtensionDTO.getLoanId().toString()));

        final Loan.LoanBuilder loanCopy = loanConverter.shallowCopy(latestLoan)
            .term(config.getExtendTermDays())
            .requestedDate(LocalDateTime.now())
            .dueDate(latestLoan.getDueDate().plusDays(config.getExtendTermDays()));

        resourceUpdateStrategy.updateResource(latestLoan);

        final Loan extendedLoan = loanRepository.save(loanCopy.build());
        return loanConverter.convertExtendedLoan(extendedLoan);
    }

    private void checkConstraints(Serializable objectToValidate) throws InvalidInputException {
        final Set<ConstraintViolation<Serializable>> constraintViolations = validator.validate(objectToValidate);
        if (!constraintViolations.isEmpty()) {
            throw new InvalidInputException(constraintViolations);
        }
    }

}