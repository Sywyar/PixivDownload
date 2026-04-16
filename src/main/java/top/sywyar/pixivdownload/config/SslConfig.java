package top.sywyar.pixivdownload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SSL / HTTPS 相关配置（前缀 {@code ssl}）。
 *
 * <p>HTTPS 证书本身通过标准 {@code server.ssl.*} 属性配置；
 * 本类仅负责管理额外的重定向行为。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ssl")
public class SslConfig {

    /**
     * 证书类型，对应配置文件中的 {@code ssl.type}。
     * {@code pem}：PEM 证书（{@code server.ssl.certificate} + {@code server.ssl.certificate-private-key}）；
     * {@code jks}：JKS/PKCS12 证书库（{@code server.ssl.key-store} + 相关属性）。
     */
    private String type = "pem";

    /**
     * 是否在 {@link #httpRedirectPort} 监听 HTTP 请求并将其 301 重定向到 HTTPS 端口。
     * 仅在对应证书已配置时生效。
     */
    private boolean httpRedirect = false;

    /**
     * HTTP 重定向监听端口，默认 80。
     */
    private int httpRedirectPort = 80;
}
