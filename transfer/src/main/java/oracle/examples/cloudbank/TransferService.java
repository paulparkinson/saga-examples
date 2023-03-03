package oracle.examples.cloudbank;

import io.narayana.lra.Current;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

//import jakarta.annotation.PostConstruct;
import javax.annotation.PostConstruct;
//import jakarta.enterprise.context.ApplicationScoped;
import javax.enterprise.context.ApplicationScoped;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.*;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
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

@RequestScoped
//@ApplicationScoped
@Path("/transfer")
public class TransferService {

    private static final Logger log = Logger.getLogger(TransferService.class.getSimpleName());
    private URI withdrawUri;
    private URI depositUri;
    @PostConstruct
    private void initController() {
        try {
            withdrawUri = new URI("http://account.application:8080/account/withdraw");
            depositUri = new URI("http://account.application:8080/account/deposit");
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Failed to initialize " + TransferService.class.getName(), ex);
        }
    }

    @GET
    @Path("/test")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.REQUIRES_NEW)
    public Response test() {
        System.out.println("TransferService.test...");
        return Response.ok().entity("test success").build();
    }
    @POST
    @Path("/transfer")
    @Produces(MediaType.APPLICATION_JSON)
    @LRA(value = LRA.Type.REQUIRES_NEW)
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
        log.info("Started new LRA : " + lraId);
        String returnString = "";
        returnString += withdraw(fromAccount, amount);
        returnString += deposit(toAccount, amount);
        return Response.ok("transfer status:" + returnString).build();

    }
    private String withdraw(String accountName, long depositAmount) {
        log.info("withdraw accountName = " + accountName + ", depositAmount = " + depositAmount);
        WebTarget webTarget =
                ClientBuilder.newClient().target(withdrawUri).path("/")
                        .queryParam("accountName", accountName)
                        .queryParam("withdrawAmount", depositAmount);
//        String withdrawOutcome = webTarget.request().post(Entity.text("")).readEntity(String.class);
        URI lraId = Current.peek();
        log.info("withdraw lraId = " + lraId);
        String withdrawOutcome =
                webTarget.request().header(org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER,lraId)
                        .post(Entity.text("")).getEntity().toString();
        return withdrawOutcome;
    }
    private String deposit(String accountName, long depositAmount) {
        log.info("deposit accountName = " + accountName + ", depositAmount = " + depositAmount);
        WebTarget webTarget =
                ClientBuilder.newClient().target(depositUri).path("/")
                        .queryParam("accountName", accountName)
                        .queryParam("depositAmount", depositAmount);
        URI lraId = Current.peek();
        log.info("deposit lraId = " + lraId);
//        String withdrawOutcome = webTarget.request().post(Entity.text("")).readEntity(String.class);
        String depositOutcome =
                webTarget.request().header(org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER,lraId)
                        .post(Entity.text("")).getEntity().toString();
        return depositOutcome;
    }

}
