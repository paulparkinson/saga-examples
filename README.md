```  
curl  http://129.158.244.40/api/v1/account/66 | json_pp ; curl  http://129.158.244.40/api/v1/account/67 | json_pp
```
% Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
Dload  Upload   Total   Spent    Left  Speed
100   161    0   161    0     0    364      0 --:--:-- --:--:-- --:--:--   370
{
"accountBalance" : 10800,
"accountCustomerId" : null,
"accountId" : 66,
"accountName" : "testpaul1",
"accountOpenedDate" : null,
"accountOtherDetails" : null,
"accountType" : null
}
% Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
Dload  Upload   Total   Spent    Left  Speed
100   161    0   161    0     0   1410      0 --:--:-- --:--:-- --:--:--  1490
{
"accountBalance" : 10800,
"accountCustomerId" : null,
"accountId" : 67,
"accountName" : "testpaul2",
"accountOpenedDate" : null,
"accountOtherDetails" : null,
"accountType" : null
}
``` 
curl -X POST "http://localhost:8080/transfer?fromAccount=66&toAccount=67&amount=100"     
```                         
transfer status:withdraw succeeded deposit succeeded%                                                                                                                          
```  
curl  http://129.158.244.40/api/v1/account/66 | json_pp ; curl  http://129.158.244.40/api/v1/account/67 | json_pp
```
% Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
Dload  Upload   Total   Spent    Left  Speed
100   161    0   161    0     0   1264      0 --:--:-- --:--:-- --:--:--  1330
{
"accountBalance" : 10700,
"accountCustomerId" : null,
"accountId" : 66,
"accountName" : "testpaul1",
```  
curl  http://129.158.244.40/api/v1/account/66 | json_pp ; curl  http://129.158.244.40/api/v1/account/67 | json_pp
```
% Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
Dload  Upload   Total   Spent    Left  Speed
100   161    0   161    0     0    368      0 --:--:-- --:--:-- --:--:--   371
{
"accountBalance" : 10800,
"accountCustomerId" : null,
"accountId" : 66,
"accountName" : "testpaul1",
"accountOpenedDate" : null,
"accountOtherDetails" : null,
"accountType" : null
}
% Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
Dload  Upload   Total   Spent    Left  Speed
100   161    0   161    0     0   1304      0 --:--:-- --:--:-- --:--:--  1387
{
"accountBalance" : 10800,
"accountCustomerId" : null,
"accountId" : 67,
"accountName" : "testpaul2",
"accountOpenedDate" : null,
"accountOtherDetails" : null,
"accountType" : null
}
``` 
curl -X POST "http://localhost:8080/transfer?fromAccount=66&toAccount=67&amount=100" 
```                             
transfer status:withdraw succeeded deposit succeeded%                                                                                                                          
```  
curl  http://129.158.244.40/api/v1/account/66 | json_pp ; curl  http://129.158.244.40/api/v1/account/67 | json_pp
```
% Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
Dload  Upload   Total   Spent    Left  Speed
100   161    0   161    0     0   1414      0 --:--:-- --:--:-- --:--:--  1490
{
"accountBalance" : 10700,
"accountCustomerId" : null,
"accountId" : 66,
"accountName" : "testpaul1",
"accountOpenedDate" : null,
"accountOtherDetails" : null,
"accountType" : null
}
% Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
Dload  Upload   Total   Spent    Left  Speed
100   161    0   161    0     0   1312      0 --:--:-- --:--:-- --:--:--  1412
{
"accountBalance" : 10900,
"accountCustomerId" : null,
"accountId" : 67,
"accountName" : "testpaul2",
"accountOpenedDate" : null,
"accountOtherDetails" : null,
"accountType" : null
}
``` 
curl -X POST "http://localhost:8080/transfer?fromAccount=66&toAccount=67&amount=100000"      
```                     
transfer status:withdraw failed: insufficient funds%                                                                                                                           
``` 
curl -X POST "http://localhost:8080/transfer?fromAccount=66&toAccount=6799999&amount=100"
```
transfer status:withdraw succeeded deposit failed: account does not exist%                                                                                                     
