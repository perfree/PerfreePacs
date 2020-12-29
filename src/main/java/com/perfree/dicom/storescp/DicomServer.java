package com.perfree.dicom.storescp;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Dicom服务
 * @author Perfree
 */
public class DicomServer {
    private final Device device = new Device("storescp");
    private final Connection conn = new Connection();
    private final ApplicationEntity ae = new ApplicationEntity("*");
    public static File storageDir;
    private static final Logger LOG = LoggerFactory.getLogger(DicomServer.class);

    public DicomServer(){
        device.setDimseRQHandler(createServiceRegistry());
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        device.setAssociationHandler(AssociationHandlerUtil.getAssociationHandler());
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);
    }

    /**
     * 注册Dicom service
     * @return DicomServiceRegistry
     */
    private DicomServiceRegistry createServiceRegistry() {
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        serviceRegistry.addDicomService(new BasicCEchoSCP());
        serviceRegistry.addDicomService(new CStoreSCPImpl());
        return serviceRegistry;
    }


    /**
     * 初始化Dicom服务
     * @param aeHost aeHost
     * @param aePort aePort
     * @param aeTitle aeTitle
     * @param storageDirectory storageDirectory
     * @return DicomServer
     */
    public static DicomServer init(String aeHost, int aePort, String aeTitle, String storageDirectory) {
        LOG.info("初始化DicomServer->>>> aeTitle: " + aeTitle + ";aeHost:" + aeHost + ";aePort:" + aePort + "; 存储dcm目录: " + storageDirectory);
        DicomServer ds = null;
        try {
            ds = new DicomServer();
            if(aeHost != null) {
                ds.conn.setHostname(aeHost);
            }
            ds.conn.setPort(aePort);
            ds.ae.setAETitle(aeTitle);
            configureConn(ds.conn);
            ds.ae.addTransferCapability(new TransferCapability(null,"*", TransferCapability.Role.SCP,"*"));
            ds.setStorageDirectory(new File(storageDirectory));
            ExecutorService executorService = Executors.newCachedThreadPool();
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            ds.device.setScheduledExecutor(scheduledExecutorService);
            ds.device.setExecutor(executorService);
            ds.device.bindConnections();
            LOG.info("-------------------初始化DicomServer:{}成功-----------------------", aePort);
        }catch (Exception e) {
            LOG.error("初始化DicomServer出错: {}", e.getMessage());
            e.printStackTrace();
        }
        return ds;
    }

    /**
     * 设置存储目录
     * @param storageDir File storageDir
     */
    public void setStorageDirectory(File storageDir) {
        if (storageDir != null){
            boolean mkdirs = storageDir.mkdirs();
        }
        DicomServer.storageDir = storageDir;
    }

    /**
     * 配置连接
     * @param conn Connection
     */
    public static void configureConn(Connection conn){
        conn.setReceivePDULength(Connection.DEF_MAX_PDU_LENGTH);
        conn.setSendPDULength(Connection.DEF_MAX_PDU_LENGTH);
        conn.setMaxOpsInvoked(0);
        conn.setMaxOpsPerformed(0);
    }
}
