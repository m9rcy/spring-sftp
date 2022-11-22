package com.example.websftp;

import com.jcraft.jsch.ChannelSftp;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.annotation.*;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.interceptor.WireTap;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.Transformers;
import org.springframework.integration.file.FileNameGenerator;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.remote.gateway.AbstractRemoteFileOutboundGateway;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.integration.sftp.gateway.SftpOutboundGateway;
import org.springframework.integration.sftp.outbound.SftpMessageHandler;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.messaging.*;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.File;
import java.io.InputStream;

import java.util.List;

@Configuration
public class SftpConfiguration {
    
    @Autowired
    BeanFactory beanFactory;

    @Bean
    public DefaultSftpSessionFactory sftpSessionFactory(){
        DefaultSftpSessionFactory defaultSftpSessionFactory = new DefaultSftpSessionFactory();
        defaultSftpSessionFactory.setHost("0.0.0.0");
        defaultSftpSessionFactory.setPort(22);
        defaultSftpSessionFactory.setUser("demo");
        defaultSftpSessionFactory.setPassword("demo");
        defaultSftpSessionFactory.setAllowUnknownKeys(true);
        //return new CachingSessionFactory<ChannelSftp.LsEntry>(defaultSftpSessionFactory);
        return defaultSftpSessionFactory;
    }

    @Bean
    public SimpleRetryPolicy retryPolicy(){
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        return retryPolicy;
    }

    @Bean
    public FixedBackOffPolicy fixedBackOffPolicy(){
        FixedBackOffPolicy p = new FixedBackOffPolicy();
        p.setBackOffPeriod(1000);
        return p;
    }

    @Bean
    public RequestHandlerRetryAdvice retryAdvice(SimpleRetryPolicy retryPolicy, FixedBackOffPolicy fixedBackOffPolicy){
        RequestHandlerRetryAdvice retryAdvice = new RequestHandlerRetryAdvice();
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        retryTemplate.setRetryPolicy(retryPolicy);
        retryAdvice.setRetryTemplate(retryTemplate);
        return retryAdvice;
    }

    @Bean
    @ServiceActivator(inputChannel = "sftpChannel1", adviceChain = "retryAdvice")
    public MessageHandler handler(){
        SftpMessageHandler messageHandler = new SftpMessageHandler(sftpSessionFactory());
        messageHandler.setRemoteDirectoryExpression(new LiteralExpression("from"));
        messageHandler.setFileNameGenerator(new FileNameGenerator() {
            @Override
            public String generateFileName(Message<?> message) {
                System.out.println(message.getHeaders().get("fileName"));
                System.out.println(message.getPayload());
                return message.getHeaders().get("fileName").toString();
            }
        });
        return messageHandler;
    }

    @Bean
    @ServiceActivator(inputChannel = "fromSftpChannel")
    public SftpOutboundGateway getFiles() {
        SftpOutboundGateway sftpOutboundGateway = new SftpOutboundGateway(sftpSessionFactory(), "mget", "payload");
        sftpOutboundGateway.setAutoCreateDirectory(true);
        sftpOutboundGateway.setLocalDirectory(new File("./downloads/"));
        sftpOutboundGateway.setOption(AbstractRemoteFileOutboundGateway.Option.RECURSIVE);
        sftpOutboundGateway.setFileExistsMode(FileExistsMode.REPLACE_IF_MODIFIED);
        sftpOutboundGateway.setFilter(new AcceptOnceFileListFilter<>());
        sftpOutboundGateway.setOutputChannelName("fileResults");
        sftpOutboundGateway.setAdviceChain(List.of(new RequestHandlerRetryAdvice()));
        return sftpOutboundGateway;
    }

    @Bean
    @ServiceActivator(inputChannel = "sftpChannel2")
    public MessageHandler retrieveFiles() {
        SftpOutboundGateway sftpOutboundGateway = new SftpOutboundGateway(sftpSessionFactory(), "mget", "payload");
        sftpOutboundGateway.setAutoCreateDirectory(true);
        sftpOutboundGateway.setLocalDirectory(new File("./downloads/"));
        sftpOutboundGateway.setOption(AbstractRemoteFileOutboundGateway.Option.RECURSIVE);
        sftpOutboundGateway.setFileExistsMode(FileExistsMode.REPLACE_IF_MODIFIED);
        sftpOutboundGateway.setFilter(new AcceptOnceFileListFilter<>());
        sftpOutboundGateway.setOutputChannelName("fileResults");
        return sftpOutboundGateway;
    }

    @Bean
    @ServiceActivator(inputChannel = "sftpChannel4")
    public MessageHandler listFileInfo() {
        SftpOutboundGateway sftpOutboundGateway = new SftpOutboundGateway(sftpSessionFactory(), "ls", "payload");
        sftpOutboundGateway.setAutoCreateDirectory(true);
        sftpOutboundGateway.setLocalDirectory(new File("./downloads/"));
        return sftpOutboundGateway;
    }

    @Bean
    @ServiceActivator(inputChannel = "sftpChannel3")
    public MessageHandler removeFile(){
        SftpOutboundGateway sftpOutboundGateway = new SftpOutboundGateway(sftpSessionFactory(),"rm","payload");
        sftpOutboundGateway.setBeanFactory(beanFactory);
        return sftpOutboundGateway;
    }

    @Bean
    public MessageChannel fileResults() {
        DirectChannel directChannel = new DirectChannel();
        directChannel.addInterceptor(wireTap());
        return directChannel;
    }

    @Transformer( inputChannel = "errorChannel")
    public Message<?> errorChannelHandler(ErrorMessage errorMessage) throws RuntimeException {
        Message<?> failedMessage =  ((MessagingException) errorMessage.getPayload()).getFailedMessage();
        Exception exception = (Exception) errorMessage.getPayload();
        System.out.println("Error details is: " +exception.getLocalizedMessage());
        return  MessageBuilder.withPayload( exception.getMessage())
                .copyHeadersIfAbsent( failedMessage.getHeaders() )
                .build();
    }

    @Bean
    public WireTap wireTap() {
        return new WireTap("logging");
    }

    @Bean
    @ServiceActivator(inputChannel = "logging")
    public LoggingHandler logger() {
        LoggingHandler logger = new LoggingHandler(LoggingHandler.Level.INFO);
        logger.setLogExpressionString("'Files:' + payload");
        return logger;
    }

    @MessagingGateway (errorChannel = "errorChannel")
    public interface SftpGateway {
        @Gateway(requestChannel = "sftpChannel1")
        public void sendFile(@Header("fileName") String fileName, InputStream file);

        @Gateway(requestChannel = "sftpChannel2", replyChannel = "fileResults")
        public List<File> listFiles(String directory);

        @Gateway(requestChannel = "sftpChannel3")
        public boolean removeFile(String file);

        @Gateway(requestChannel = "sftpChannel4")
        public List<FileInfo> listFileInfo(String dir);
    }




}
