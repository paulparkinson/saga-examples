package oracle.examples.cloudbank;

import io.narayana.lra.client.internal.proxy.nonjaxrs.LRAParticipantRegistry;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

import javax.ws.rs.ApplicationPath;

@Component
@ApplicationPath("/")
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig()  {
        register(TransferService.class);
        register(io.narayana.lra.filter.ServerLRAFilter.class);
        register(new AbstractBinder(){
            @Override
            protected void configure() {
                bind(LRAParticipantRegistry.class)
                    .to(LRAParticipantRegistry.class);
            }
        });
    }

}
