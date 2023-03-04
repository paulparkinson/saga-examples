package oracle.examples.cloudbank;

import io.narayana.lra.Current;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.*;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

//@RequestScoped//
@ApplicationScoped
@Path("/")
public class TransferService {

    private static final Logger log = Logger.getLogger(TransferService.class.getSimpleName());
    private URI withdrawUri;
    private URI depositUri;
    private URI transferCancelUri;
    private URI transferConfirmUri;
    private URI transferProcessCancelUri;
    private URI transferProcessConfirmUri;
    @PostConstruct
    private void initController() {
        try { //todo get from config/env
            withdrawUri = new URI("http://account.application:8080/withdraw/withdraw");
            depositUri = new URI("http://account.application:8080/deposit/deposit");
            transferCancelUri = new URI("http://transfer.application:8080/cancel");
            transferConfirmUri = new URI("http://transfer.application:8080/confirm");
            transferProcessCancelUri = new URI("http://transfer.application:8080/processcancel");
            transferProcessConfirmUri = new URI("http://transfer.application:8080/processconfirm");
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Failed to initialize " + TransferService.class.getName(), ex);
        }
    }

    @GET
    @Path("/test")
    @Produces(MediaType.APPLICATION_JSON)
//    @LRA(value = LRA.Type.REQUIRES_NEW)
    public Response test() {
        System.out.println("TransferService.test...");
        return Response.ok().entity("test success").build();
    }
    @POST
    @Path("/transfer")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.REQUIRES_NEW, end = true)
    public Response transfer(@QueryParam("fromAccount") String fromAccount,
                             @QueryParam("toAccount") String toAccount,
                             @QueryParam("amount") long amount,
                             @Context UriInfo uriInfo,
                             @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                             @Context ContainerRequestContext containerRequestContext)
    {
        if (lraId == null) {
            return Response.serverError().entity("Failed to create LRA").build();
        }
        log.info("Started new LRA/transfer Id: " + lraId);
        boolean isCompensate = false;
        String returnString = "";
        returnString += withdraw(fromAccount, amount);
        log.info(returnString);
        if (returnString.contains("succeeded")) {
            returnString += deposit(toAccount, amount);
            log.info(returnString);
            if (returnString.contains("failed")) isCompensate = true; //deposit failed
        } else isCompensate = true; //withdraw failed
        log.info("LRA/transfer action will be " + (isCompensate?"cancel":"confirm"));
        WebTarget webTarget = ClientBuilder.newClient().target(isCompensate?transferCancelUri:transferConfirmUri);
        webTarget.request().header(LRA_HTTP_CONTEXT_HEADER,lraId).header("mytestheader","mytestvalue")
                .post(Entity.text("")).getEntity().toString();
        return Response.ok("transfer status:" + returnString).build();

    }

    private String withdraw(String accountName, long depositAmount) {
        log.info("withdraw accountName = " + accountName + ", depositAmount = " + depositAmount);
        WebTarget webTarget =
                ClientBuilder.newClient().target(withdrawUri).path("/")
                        .queryParam("accountName", accountName)
                        .queryParam("amount", depositAmount);
        URI lraId = Current.peek();
        log.info("withdraw lraId = " + lraId);
        String withdrawOutcome =
                webTarget.request().header(LRA_HTTP_CONTEXT_HEADER,lraId)
                        .post(Entity.text("")).getEntity().toString();
        return withdrawOutcome;
    }
    private String deposit(String accountName, long depositAmount) {
        log.info("deposit accountName = " + accountName + ", depositAmount = " + depositAmount);
        WebTarget webTarget =
                ClientBuilder.newClient().target(depositUri).path("/")
                        .queryParam("accountName", accountName)
                        .queryParam("amount", depositAmount);
        URI lraId = Current.peek();
        log.info("deposit lraId = " + lraId);
        String depositOutcome =
                webTarget.request().header(LRA_HTTP_CONTEXT_HEADER,lraId)
                        .post(Entity.text("")).getEntity().toString();
        return depositOutcome;
    }




    @POST
    @Path("/processconfirm")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.MANDATORY)
    public Response processconfirm(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException {
        log.info("Process confirm for transfer : " + lraId);
        return Response.ok().build();
    }

    @POST
    @Path("/processcancel")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.MANDATORY)
    public Response processcancel(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId) throws NotFoundException {
        log.info("Process cancel for transfer : " + lraId);
        return Response.ok().build();
    }


    // The following two methods could be in an external client.
    // They are included here for convenience.
    // The transfer method makes a Rest call to confirm or commit.
    // The confirm or commit method suspends the LRA (via NOT_SUPPORTED)
    // The confirm or commit method then proceeds to make a Rest call to the "processconfirm" or "processcommit" method
    // The "processconfirm" and "processcommit" methods import the LRA (via MANDATORY)
    //  and end the LRA implicitly accordingly upon return.
    @POST
    @Path("/confirm")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.NOT_SUPPORTED)
    public Response confirm(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                            @HeaderParam("mytestheader") String mytestheader) throws NotFoundException {
        log.info("Received Confirmation for transfer : " + lraId);
        log.info("Received Confirmation for transfer mytestheader: " + mytestheader);
        String confirmOutcome =
                ClientBuilder.newClient().target(transferProcessConfirmUri).request()
                        .header(LRA_HTTP_CONTEXT_HEADER,lraId)
                        .post(Entity.text("")).getEntity().toString();
        return Response.ok(confirmOutcome).build();
    }

    @POST
    @Path("/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.NOT_SUPPORTED, cancelOn = Response.Status.OK)
    public Response cancel(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                           @HeaderParam("mytestheader") String mytestheader) throws NotFoundException {
        log.info("Received Confirmation for transfer : " + lraId);
        log.info("Received Confirmation for transfer mytestheader: " + mytestheader);
        String confirmOutcome =
                ClientBuilder.newClient().target(transferProcessCancelUri).request()
                        .header(LRA_HTTP_CONTEXT_HEADER,lraId)
                        .post(Entity.text("")).getEntity().toString();
        return Response.ok(confirmOutcome).build();
    }

}
