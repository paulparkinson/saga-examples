
2 services: transfer and account
No state is held in the services and so the account service can be scaled, crashed, etc. thus providing HA
account is accessible via internet, transfer is not.

Calls are made to the transfer account `tranfer method` providing a fromAccount, toAccount, and amount.
The tranfer service then makes a call to the account service `withdraw` method for the `fromAccount` and, if successful, makes a call to the account service `deposit` method for the `toAccount`

1. create two accounts and add balances to them. Eg (replacing the accountName with one of your own)...

```
curl -X POST -H "Content-Type: application/json" -d '{"accountName": "testpaul1@example.com", "accountBalance": 1000}' http://129.158.244.40/api/v1/accountWithBalance/
```
{"accountId":64,"accountName":"testpaul1@example.com","accountType":null,"accountCustomerId":null,"accountOpenedDate":null,"accountOtherDetails":null,"accountBalance":1000}%
```
curl -X POST -H "Content-Type: application/json" -d '{"accountName": "testpaul2@example.com", "accountBalance": 1000}}' http://129.158.244.40/api/v1/accountWithBalance/
```
{"accountId":65,"accountName":"testpaul2@example.com","accountType":null,"accountCustomerId":null,"accountOpenedDate":null,"accountOtherDetails":null,"accountBalance":1000}%

2. start port-forward for transfer service
   kubectl port-forward services/transfer -n application 8080:8080

3. make a transfer request
```
curl -X POST "http://localhost:8080/transfer?fromAccount=testpaul1@example.com&toAccount=testpaul2@example.com&Amount=100"
```






Cleanup. You can query the account and delete in the following way...
```
curl -X DELETE http://129.158.244.40/api/v1/account/63
```
```
curl  http://129.158.244.40/api/v1//account/getAccountsByCustomerName/testpaul1@example.com
```

