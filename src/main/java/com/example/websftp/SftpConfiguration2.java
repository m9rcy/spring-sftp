package com.example.websftp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.GatewayHeader;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.remote.gateway.AbstractRemoteFileOutboundGateway;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.messaging.Message;

import java.io.File;
import java.util.List;

@Configuration
public class SftpConfiguration2 {

    @Bean
    public DefaultSftpSessionFactory sf(){
        DefaultSftpSessionFactory defaultSftpSessionFactory = new DefaultSftpSessionFactory();
        defaultSftpSessionFactory.setHost("0.0.0.0");
        defaultSftpSessionFactory.setPort(22);
        defaultSftpSessionFactory.setUser("demo");
        defaultSftpSessionFactory.setPassword("demo");
        defaultSftpSessionFactory.setAllowUnknownKeys(true);
        //return new CachingSessionFactory<ChannelSftp.LsEntry>(defaultSftpSessionFactory);
        return defaultSftpSessionFactory;
    }

    // Flow
    @Bean
    IntegrationFlow flow(DefaultSftpSessionFactory sf) {
        return IntegrationFlows.from(Gate.class)
//                .handle(Sftp.outboundGateway(sf, AbstractRemoteFileOutboundGateway.Command.GET, "payload")
//                        .localDirectoryExpression("'./downloads/'"))
                .handle(Sftp.outboundGateway(sf, AbstractRemoteFileOutboundGateway.Command.MGET, "payload")
                        .autoCreateDirectory(true)
                        .localDirectory(new File("./downloads/"))
                        .options(AbstractRemoteFileOutboundGateway.Option.RECURSIVE)
                        .fileExistsMode(FileExistsMode.REPLACE_IF_MODIFIED)
                        .filter(new AcceptOnceFileListFilter<>()))
                .log(message -> message.getPayload())
                .log(message -> message.getHeaders())
                .split()
                .log(message -> message.getPayload())
                .log(message -> message.getHeaders())
                //.transform(new FileToStringTransformer())
                .handle(Sftp.outboundGateway(sf, AbstractRemoteFileOutboundGateway.Command.RM, "headers['file_remoteDirectory'] + payload.name").remoteFileSeparator("/"))
                .nullChannel();
//                .log(message -> message.getHeaders())
//                    .transform(new FileToStringTransformer())
//                    .log(message -> message.getPayload())
//                    .handle(Sftp.outboundGateway(sf, AbstractRemoteFileOutboundGateway.Command.RM, "headers['file_remoteDirectory'] + headers['file_remoteFile']").remoteFileSeparator("/"))
    }

//    @Bean
//    IntegrationFlow flow(DefaultSftpSessionFactory sf) {
//        return f -> f
//                .log()
//                .route(Message.class, m -> m.getHeaders().get("method", String.class), r -> r
//                        .subFlowMapping("getFile", f1 -> f1
//                                .handle(Sftp.outboundGateway(sf, AbstractRemoteFileOutboundGateway.Command.GET, "payload")
//                                        .localDirectoryExpression("'/tmp'"))
//                                .transform(new FileToStringTransformer())
//                                .log(message -> message.getPayload())
//                        )
//                                //.transform(Transformers.fromJson(Foo.class)))
//                        .subFlowMapping("removeFile", f2 -> f2
//                                .handle(Sftp.outboundGateway(sf, AbstractRemoteFileOutboundGateway.Command.RM, "payload"))));
//    }

    public interface Gate {
        public File getFiles(String directory);
        public File getFile(String filename);
        public boolean removeFile(String file);
    }
}
