package com.perfree;

import com.perfree.dicom.storescp.DicomServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * 开启pacs服务
     * @param storageDir storageDir
     * @param aeTitle aeTitle
     * @param ports ports
     * @return Map<String, DicomServer>
     */
    @Bean
    public Map<String, DicomServer> dicomServers(@Value("${pacs.dcmSavePath}") String storageDir, @Value("${pacs.aetitle}") String aeTitle, @Value("#{'${pacs.ports}'.split(',')}") List<Integer> ports){
        Map<String, DicomServer> dicomServers = new HashMap<>();
        for (int port:ports) {
            dicomServers.put("DICOM_SERVER_AT_" + port, DicomServer.init(null, port, aeTitle, storageDir));
        }
        return dicomServers;
    }
}
