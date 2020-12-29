package com.perfree.dicom.common;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import org.dcm4che3.net.pdu.CommonExtendedNegotiation;
import org.dcm4che3.util.StringUtils;

public class RelatedGeneralSOPClasses {
    private final HashMap<String, CommonExtendedNegotiation> commonExtNegs = new HashMap();

    public RelatedGeneralSOPClasses() {
    }

    public void init(Properties props) {
        Iterator var2 = props.stringPropertyNames().iterator();

        while(var2.hasNext()) {
            String cuid = (String)var2.next();
            this.commonExtNegs.put(cuid, new CommonExtendedNegotiation(cuid, "1.2.840.10008.4.2", StringUtils.split(props.getProperty(cuid), ',')));
        }

    }

    public CommonExtendedNegotiation getCommonExtendedNegotiation(String cuid) {
        CommonExtendedNegotiation commonExtNeg = (CommonExtendedNegotiation)this.commonExtNegs.get(cuid);
        return commonExtNeg != null ? commonExtNeg : new CommonExtendedNegotiation(cuid, "1.2.840.10008.4.2", new String[0]);
    }
}