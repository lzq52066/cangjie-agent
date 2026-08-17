package cn.cangjiecloud.agent.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
@Component
public class StartedListener {

    @EventListener
    public void onStarted(ApplicationStartedEvent event) throws UnknownHostException {
        String host = InetAddress.getLocalHost().getHostAddress();
        String port = event.getApplicationContext().getEnvironment().getProperty("server.port", "8080");
        log.info("============================================");
        log.info("  CangJie Agent Started Success           ");
        log.info("  Local:    http://localhost:{}/admin/", port);
        log.info("  Network:  http://{}:{}/admin/", host, port);
        log.info("  Swagger:  http://localhost:{}/doc.html", port);
        log.info("  Chat:     http://localhost:{}/chat/", port);
        log.info("============================================");
    }
}
