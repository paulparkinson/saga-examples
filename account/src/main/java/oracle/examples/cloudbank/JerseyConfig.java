package oracle.examples.cloudbank;

import io.narayana.lra.client.internal.proxy.nonjaxrs.LRAParticipantRegistry;
import oracle.examples.cloudbank.services.AccountsDepositService;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletProperties;
import org.springframework.stereotype.Component;

import javax.ws.rs.ApplicationPath;
import java.net.URISyntaxException;

@Component
@ApplicationPath("/")
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() throws URISyntaxException {
        register(AccountsDepositService.class);
        register(io.narayana.lra.filter.ClientLRARequestFilter.class);
        register(io.narayana.lra.filter.ClientLRAResponseFilter.class);
        register(io.narayana.lra.filter.ServerLRAFilter.class);
        register(new AbstractBinder(){
            @Override
            protected void configure() {
                bind(LRAParticipantRegistry.class)
                    .to(LRAParticipantRegistry.class);
            }
        });
        property(ServletProperties.FILTER_FORWARD_ON_404, true);
    }

}
