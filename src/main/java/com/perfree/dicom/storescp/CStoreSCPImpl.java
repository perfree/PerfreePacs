package com.perfree.dicom.storescp;

import com.perfree.common.SpringBeanUtils;
import com.perfree.dicom.common.DicomFileEvent;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.util.SafeClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * CStoreSCP实现类,接收到CStoreSCP消息会走这里
 * @author Perfree
 */
@Component
public class CStoreSCPImpl extends BasicCStoreSCP {
    private int status;
    private static final Logger LOG = LoggerFactory.getLogger(CStoreSCPImpl.class);
    private static final String DCM_EXT = ".dcm";
    public CStoreSCPImpl() {
        super("*");
    }

    @Override
    protected void store(Association as, PresentationContext pc, Attributes rq, PDVInputStream data, Attributes rsp){
        rsp.setInt(Tag.Status, VR.US, status);
        String ipAddress  = as.getSocket().getInetAddress().getHostAddress();
        String associationName = as.toString();
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        String tsuid = pc.getTransferSyntax();
        // 不建议在这里进行dicom文件解析,容易出现问题,这里只接收保存
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String dateFormat = sdf.format(new Date());
        File file = new File(DicomServer.storageDir + File.separator + dateFormat, iuid + DCM_EXT);
        LOG.info("ip:{},associationName{}",ipAddress,associationName);
        try {
            storeTo(as, as.createFileMetaInformation(iuid, cuid, tsuid), data, file);
            if(!file.exists()){
                LOG.error("Dicom文件 {} 不存在! 信息--> ipAddress: {}  associationName: {}  sopclassuid: {}  sopinstanceuid: {} transfersyntax: {}", file.getAbsolutePath(), ipAddress, associationName, cuid, iuid, tsuid);
                return;
            }
            DicomFileEvent dicomFileEvent = new DicomFileEvent(file);
            SpringBeanUtils.getApplicationContext().publishEvent(dicomFileEvent);
        }catch (Exception e) {
            e.printStackTrace();
            LOG.error("保存DICOM文件出现异常: " + e.getMessage());
        }

    }

    /**
     * 存储dcm文件
     * @param as Association
     * @param fmi Attributes
     * @param data PDVInputStream
     * @param file File
     * @throws IOException IOException
     */
    private void storeTo(Association as, Attributes fmi, PDVInputStream data, File file) throws IOException {
        LOG.info("{}: 正在写入文件 {}", as, file);
        boolean mkdirs = file.getParentFile().mkdirs();
        DicomOutputStream out = new DicomOutputStream(file);
        try {
            out.writeFileMetaInformation(fmi);
            data.copyTo(out);
        } finally {
            SafeClose.close(out);
        }
    }
}
