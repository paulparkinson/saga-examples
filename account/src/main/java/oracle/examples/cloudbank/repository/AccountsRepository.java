package oracle.examples.cloudbank.repository;

import oracle.examples.cloudbank.model.Accounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountsRepository extends JpaRepository <Accounts, Long> {
    List<Accounts> findAccountsByAccountNameContains (String accountName);

    List<Accounts> findByAccountCustomerId(String customerId);

}
