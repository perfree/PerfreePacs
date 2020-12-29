package com.perfree.dicom.storescu;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * StoreScu工具类(用于上传dicom文件至pacs,基于http的上传不再封装,可参考接口平台上传代码)
 * 该工具类只是基于Dcm4che中的dcm4che-tool-storescu进行简单的封装,支持如下:
 * 1. 发送单个dicom文件或整个目录的dicom文件
 * 2. 直接调用 StoreSCU.main
 * 参考文档: https://github.com/dcm4che/dcm4che/tree/master/dcm4che-tool/dcm4che-tool-storescu
 */
public class StoreScuUtil {

    /**
     * 发送单个dicom文件
     * @param file dicom文件或文件夹
     * @param aeTitle aeTitle
     * @param ip ip
     * @param port port
     */
    public static void sendDicom(File file,String aeTitle, String ip, String port) throws Exception{
        if (!file.exists()) {
           throw new FileNotFoundException("文件或文件夹不存在");
        }
        String server = "%s@%s:%s";
        String[] param = {"-c",String.format(server, aeTitle,ip,port),file.getAbsolutePath()};
        CustomStoreScu.sendDicom(param);
    }

    /**
     * 直接调用 StoreSCU.main
     * 参数可参考https://github.com/dcm4che/dcm4che/tree/master/dcm4che-tool/dcm4che-tool-storescu
     * @param args 参数列表
     */
    public static void storeScu(String[] args) throws Exception{
        CustomStoreScu.sendDicom(args);
    }


    /**
     * 调用示例
     */
    public static void main(String[] args) throws Exception {
        // 发送dicom或文件夹内所有dicom
        File file = new File("C:\\Users\\Administrator\\Desktop\\dicom img\\1");
        sendDicom(file,"xx","127.0.0.1","106");

        // 直接调用StoreSCU.main
        String[] param = {"-c", "aeTitle@localhost:106",file.getAbsolutePath()};
        storeScu(param);
    }
}
