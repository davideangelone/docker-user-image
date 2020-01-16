package it.test.docker;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@Slf4j
public class DockerUserImageApplication implements ApplicationListener<WebServerInitializedEvent> {
	
	private static String loginUrl;
	private static String userImageUrl;
	
	public static void main(String[] args) {
		SpringApplication.run(DockerUserImageApplication.class, args);
		
		log.info("==== Variabili d'ambiente ====");
		
		for (Map.Entry<String, String> e : new TreeMap<String, String>(System.getenv()).entrySet()) {
			log.info("[" + e.getKey() + "]=[" + e.getValue() + "]");
		}
		
		log.info("==== System properties ====");
		for (Map.Entry<Object, Object> e : new TreeMap<Object, Object>(System.getProperties()).entrySet()) {
			log.info("[" + e.getKey() + "]=[" + e.getValue() + "]");
		}
			
		log.info("LOGIN (internal url) : " + loginUrl);
		log.info("USERIMAGE (internal url) : " + userImageUrl);
		
	}
	
	
	@Override
	public void onApplicationEvent(WebServerInitializedEvent event) {
        String ip;
		try {
			ip = InetAddress.getLocalHost().getHostAddress();
	        loginUrl = "http://" + ip + ":" + event.getWebServer().getPort() + "/login";
	        userImageUrl = "http://" + ip + ":" + event.getWebServer().getPort() + "/userinfo";
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

}
