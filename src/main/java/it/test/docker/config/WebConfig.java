package it.test.docker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@Order(value = 1)
public class WebConfig extends WebSecurityConfigurerAdapter {
	
    @Override
    protected void configure(HttpSecurity http) throws Exception {
    	
    	http
    	    .csrf().disable()
        	.oauth2Login()
            	.redirectionEndpoint()
                	.baseUri("/authorization-code/callback");
    	
    }
    
}
