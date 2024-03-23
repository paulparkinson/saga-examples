package oracle.examples.cloudbank.services;

import oracle.examples.cloudbank.model.Account;
import oracle.examples.cloudbank.model.Journal;
import oracle.examples.cloudbank.repository.AccountRepository;
import oracle.examples.cloudbank.repository.JournalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class AccountAndJournalAdminService {

    final AccountRepository accountRepository;
    final JournalRepository journalRepository;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public AccountAndJournalAdminService(AccountRepository accountRepository, JournalRepository journalRepository) {

        this.accountRepository = accountRepository;
        this.journalRepository = journalRepository;
    }

    // Get Account with specific Account ID
    @GetMapping("/account/{accountId}")
    public ResponseEntity<Account> getAccountById(@PathVariable("accountId") long accountId) {
        log.info("ACCOUNT: getAccountById:" + accountId);
        Optional<Account> accountData = accountRepository.findById(accountId);
        try {
            return accountData.map(account -> new ResponseEntity<>(account, HttpStatus.OK))
                    .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/account/getAccounts/{customerId}")
    public ResponseEntity<List<Account>> getAccountsByCustomerId(@PathVariable("customerId") String customerId) {
        log.info("ACCOUNT: getAccountsByCustomerId");
        try {
            List<Account> accountData = new ArrayList<Account>();
            accountData.addAll(accountRepository.findByAccountCustomerId(customerId));
            if (accountData.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(accountData, HttpStatus.OK);
        } catch (Exception e) {
                return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
            }
    }
    @GetMapping("/account/getAccountsByCustomerName/{customerName}")
    public ResponseEntity<List<Account>> getAccountsByCustomerName(@PathVariable("customerName") String customerName) {
        log.info("ACCOUNT: getAccountsByCustomerName:" + customerName);
        try {
            List<Account> accountData = new ArrayList<Account>();
            accountData.addAll(accountRepository.findAccountsByAccountNameContains(customerName));
            if (accountData.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(accountData, HttpStatus.OK);
        } catch (Exception e) {
                return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/account")
    public ResponseEntity<Account> createAccount(@RequestBody Account account) {
        log.info("ACCOUNT: createAccount");
        try {
            Account _account = accountRepository.save(new Account(
                    account.getAccountName(),
                    account.getAccountType(),
                    account.getAccountOtherDetails(),
                    account.getAccountCustomerId()));
            return new ResponseEntity<>(_account, HttpStatus.CREATED);

        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/createAccountWith1000Balance")
    public ResponseEntity<Account> createAccountWith1000Balance(@RequestBody Account account) {
        log.info("ACCOUNT: createAccount with $1000 balance");
        try {
            Account entity = new Account(
                    account.getAccountName(),
                    account.getAccountType(),
                    account.getAccountOtherDetails(),
                    account.getAccountCustomerId());
            entity.setAccountBalance(1000);
            Account _account = accountRepository.save(entity);
            return new ResponseEntity<>(_account, HttpStatus.CREATED);

        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @DeleteMapping("/account/{accountId}")
    public ResponseEntity<HttpStatus> deleteAccount(@PathVariable("accountId") long accountId) {
        log.info("ACCOUNT: deleteAccount");
        try {
            accountRepository.deleteById(accountId);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/journals")
    public ResponseEntity<List<Journal>> getAllJournals() {
        log.info("JOURNAL: getAllJournals");
        try {
            List<Journal> journalData = new ArrayList<Journal>();
            journalData.addAll(journalRepository.findAll());
            if (journalData.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(journalData, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}

