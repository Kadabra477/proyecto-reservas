package com.example.reservafutbol.Configuracion;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

@Configuration
public class SSLConfig {

    @Bean
    public CloseableHttpClient httpClient() throws Exception {
        // Crear un TrustManager que no valide los certificados
        TrustManager[] trustAllCertificates = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        // Instalar un contexto SSL con el TrustManager personalizado
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCertificates, new java.security.SecureRandom());

        // Crear un cliente HTTP sin validación de host
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        return HttpClients.custom()
                .setSslcontext(sc)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE) // Desactivar verificación del hostname
                .setConnectionManager(cm)
                .build();
    }
}