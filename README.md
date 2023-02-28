
To Install:
oractl commands..
create --appName cloudbank
bind --appName cloudbank --serviceName account
deploy --appName cloudbank --serviceName account --isRedeploy false --imageVersion 0.1 --jarLocation /Users/pparkins/Downloads/src/github.com/andytael/cloudbank/account/target/account-0.0.1-SNAPSHOT.jar
(assuming `cloudbank` app/namespace has already been created)
kubectl apply -f k8s_transfersaga.yaml -n cloudbank

Sample Run:
$ curl http://account.cloudbank.svc.cluster.local:8082/accountService/api/account
[]
$ curl http://transfer.cloudbank.svc.cluster.local:8081/trip-service/api/trip
[]

$ curl -X POST -d '' http://transfer.cloudbank.svc.cluster.local:8081/trip-service/api/trip?accountName=Mercury&accountNumber=A123

{
"details": [{
"encodedId": "http%3A%2F%2Fotmm-tcs.otmm.svc.cluster.local%3A9000%2Fapi%2Fv1%2Flra-coordinator%2F64fc621a-1521-4bae-a481-975061cf4bda",
"id": "http://otmm-tcs.otmm.svc.cluster.local:9000/api/v1/lra-coordinator/64fc621a-1521-4bae-a481-975061cf4bda",
"name": "Mercury",
"status": "PROVISIONAL",
"type": "Hotel"
}, {
"details": [],
"encodedId": "http%3A%2F%2Fotmm-tcs.otmm.svc.cluster.local%3A9000%2Fapi%2Fv1%2Flra-coordinator%2F64fc621a-1521-4bae-a481-975061cf4bda",
"id": "http://otmm-tcs.otmm.svc.cluster.local:9000/api/v1/lra-coordinator/64fc621a-1521-4bae-a481-975061cf4bda",
"name": "A123",
"status": "PROVISIONAL",
"type": "Flight"
}],
"encodedId": "http%3A%2F%2Fotmm-tcs.otmm.svc.cluster.local%3A9000%2Fapi%2Fv1%2Flra-coordinator%2F64fc621a-1521-4bae-a481-975061cf4bda",
"id": "http://otmm-tcs.otmm.svc.cluster.local:9000/api/v1/lra-coordinator/64fc621a-1521-4bae-a481-975061cf4bda",
"name": "Trip",
"status": "PROVISIONAL",
"type": "Trip"
}

//Confirm the Booking

$ curl --location --request PUT -H "Long-Running-Action: http://otmm-tcs.otmm.svc.cluster.local:9000/api/v1/lra-coordinator/64fc621a-1521-4bae-a481-975061cf4bda" -d '' http://transfer.cloudbank.svc.cluster.local:8081/trip-service/api/trip/http%3A%2F%2Fotmm-tcs.otmm.svc.cluster
.local%3A9000%2Fapi%2Fv1%2Flra-coordinator%2F64fc621a-1521-4bae-a481-975061cf4bda

//Get the Booking Details
$ curl http://transfer.cloudbank.svc.cluster.local:8081/trip-service/api/trip

[{
"details": [{
"encodedId": "http%3A%2F%2Fotmm-tcs.otmm.svc.cluster.local%3A9000%2Fapi%2Fv1%2Flra-coordinator%2F64fc621a-1521-4bae-a481-975061cf4bda",
"id": "http://otmm-tcs.otmm.svc.cluster.local:9000/api/v1/lra-coordinator/64fc621a-1521-4bae-a481-975061cf4bda",
"name": "Mercury",
"status": "CONFIRMED",
"type": "Hotel"
}, {
"details": [],
"encodedId": "http%3A%2F%2Fotmm-tcs.otmm.svc.cluster.local%3A9000%2Fapi%2Fv1%2Flra-coordinator%2F64fc621a-1521-4bae-a481-975061cf4bda",
"id": "http://otmm-tcs.otmm.svc.cluster.local:9000/api/v1/lra-coordinator/64fc621a-1521-4bae-a481-975061cf4bda",
"name": "A123",
"status": "CONFIRMED",
"type": "Flight"
}],
"encodedId": "http%3A%2F%2Fotmm-tcs.otmm.svc.cluster.local%3A9000%2Fapi%2Fv1%2Flra-coordinator%2F64fc621a-1521-4bae-a481-975061cf4bda",
"id": "http://otmm-tcs.otmm.svc.cluster.local:9000/api/v1/lra-coordinator/64fc621a-1521-4bae-a481-975061cf4bda",
"name": "Trip",
"status": "CONFIRMED",
"type": "Trip"
}]