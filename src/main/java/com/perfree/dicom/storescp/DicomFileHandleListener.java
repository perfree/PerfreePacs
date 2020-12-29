package com.perfree.dicom.storescp;

import com.perfree.dicom.common.DicomFileEvent;
import com.perfree.dicom.common.DicomReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Dicom文件异步处理监听
 * 如果在接收到dicom时直接解析进行业务处理,可能会造成一系列问题,故将此部分转化为异步处理,不影响接收服务
 * @author Perfree
 */
@Component
public class DicomFileHandleListener {
    private static final Logger LOG = LoggerFactory.getLogger(DicomFileHandleListener.class);
    //异步监听器
    @Async
    @EventListener
    public void dualEven(DicomFileEvent event){
        try{
            LOG.info("-----------------------------------dicom解析开始-------------------------------------------");
            DicomReader dicomReader = new DicomReader(event.getFile());
            // TODO 做你想做的事情吧,骚年
            /*
             * tips: 建议将信息按照PatientID存到患者信息库,文件按照SOPInstanceUID存库,
             * 按照标准来说,一个患者可能会有很多个dicom文件,而每个dicom文件的SOPInstanceUID都是不一样的
             *
             * 下面是小课堂时间:
             * SeriesInstanceUID是一个系列的全局唯一标识符
             * SOPInstanceUID是一个全局唯一标识符
             * 一系列可以有多个DICOM文件,所以一个系列内每个Dicom将共享相同的SeriesInstanceUID ，
             * 但每个文件都有自己的SOPInstanceUID
             *
             * Dicom深入了解参考网站http://www.dclunie.com/medical-image-faq/html/part1.html
             */
            LOG.info("PatientName: {},PatientID:{},PatientAge:{},PatientSex:{},PatientBirthDay:{},TransferSyntaxUID:{},SOPInstanceUID:{}",
                    dicomReader.getPatientName(),dicomReader.getPatientID(),dicomReader.getPatientAge(),
                    dicomReader.getPatientSex(),dicomReader.getPatientBirthDay(),dicomReader.getTransferSyntaxUID(),
                    dicomReader.getSOPInstanceUID());
            LOG.info("-----------------------------------dicom解析完毕-------------------------------------------");
        }catch(Exception e){
            e.printStackTrace();
            LOG.error(e.getMessage());
        }
    }
}
