package com.example.websftp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@DependsOn("flow")
@RestController
public class WebSftpController {

    @Autowired
    SftpConfiguration.SftpGateway gateway;

    @Autowired
    SftpConfiguration2.Gate gate;

    @PostMapping("/upload")
    public ResponseEntity uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        gateway.sendFile(file.getOriginalFilename(), file.getInputStream());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/files")
    public ResponseEntity<List<FileInfo>> getListFiles() {
        List<FileInfo> fileInfos = gateway.listFiles("/sftp/from/").stream().map(file -> {
            String filename = file.getName();
            String url = MvcUriComponentsBuilder
                    .fromMethodName(WebSftpController.class, "getFile", file.getName()).build().toString();
            return new FileInfo(filename, url);
        }).collect(Collectors.toList());

        for (FileInfo fileInfo: fileInfos) {
            gateway.removeFile("/sftp/from/" + fileInfo.getName());
        }

        return ResponseEntity.status(HttpStatus.OK).body(fileInfos);
    }

    @GetMapping("/files2")
    public ResponseEntity<List<File>> getListFiles2() {
        System.out.println("Fetched: {}"+ gate.getFiles("/sftp/from/"));
        return ResponseEntity.status(HttpStatus.OK).body(Collections.EMPTY_LIST);
    }

    @GetMapping("/files/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> getFile(@PathVariable String filename) {
//        Resource file = storageService.load(filename);
//        return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"").body(file);
        return ResponseEntity.ok(null);
    }
}
