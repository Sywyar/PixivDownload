package top.sywyar.pixivdownload.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 当 HTTPS 已配置且 {@code ssl.http-redirect=true} 时，
 * 在 {@code ssl.http-redirect-port}（默认 80）额外开启一个 HTTP 连接器，
 * 所有 HTTP 请求将被 Tomcat 自动 301 重定向到 HTTPS 端口。
 *
 * <p>支持两种证书类型（与 Spring Boot 优先级一致）：
 * <ol>
 *   <li><b>PEM</b>：{@code server.ssl.certificate} + {@code server.ssl.certificate-private-key}
 *   <li><b>JKS</b>：{@code server.ssl.key-store} + {@code server.ssl.key-store-password}
 * </ol>
 * 若两种配置同时存在，PEM 优先（Spring Boot {@code WebServerSslBundle} 行为一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpsWebServerCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final SslConfig sslConfig;
    private final Environment environment;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (!sslConfig.isHttpRedirect()) {
            return;
        }
        if (!isSslConfigured()) {
            log.warn("ssl.http-redirect=true 但未检测到 HTTPS 证书配置（PEM: server.ssl.certificate + server.ssl.certificate-private-key，或 JKS: server.ssl.key-store），跳过 HTTP 重定向");
            return;
        }

        int httpsPort = environment.getProperty("server.port", Integer.class, 6999);
        int httpPort = sslConfig.getHttpRedirectPort();

        log.info("HTTP→HTTPS 重定向已启用：监听 HTTP 端口 {}，重定向到 HTTPS 端口 {}", httpPort, httpsPort);

        factory.addAdditionalTomcatConnectors(createHttpConnector(httpPort, httpsPort));
        factory.addContextCustomizers(context -> {
            SecurityConstraint constraint = new SecurityConstraint();
            constraint.setUserConstraint("CONFIDENTIAL");
            SecurityCollection collection = new SecurityCollection();
            collection.addPattern("/*");
            constraint.addCollection(collection);
            context.addConstraint(constraint);
        });
    }

    private boolean isSslConfigured() {
        boolean hasPem = environment.containsProperty("server.ssl.certificate")
                && environment.containsProperty("server.ssl.certificate-private-key");
        boolean hasJks = environment.containsProperty("server.ssl.key-store");
        if (hasPem && hasJks) {
            log.warn("同时检测到 PEM 证书（server.ssl.certificate）和 JKS 证书（server.ssl.key-store）配置，将优先使用 PEM 证书");
        }
        return hasPem || hasJks;
    }

    private Connector createHttpConnector(int httpPort, int httpsPort) {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        return connector;
    }
}
