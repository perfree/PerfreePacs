package com.perfree.dicom.storescp;

import org.dcm4che3.net.Association;
import org.dcm4che3.net.AssociationHandler;
import org.dcm4che3.net.State;
import org.dcm4che3.net.pdu.AAssociateAC;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.UserIdentityAC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AssociationHandlerUtil {
    private static final Logger LOG = LoggerFactory.getLogger(AssociationHandlerUtil.class);

    public static AssociationHandler getAssociationHandler() {
        return new AssociationHandler(){

            @Override
            protected AAssociateAC makeAAssociateAC(Association as, AAssociateRQ rq, UserIdentityAC arg2) throws IOException {
                State st = as.getState();
                LOG.info("makeAAssociateAC: {}  Associate State: {}  Associate State Name: {}", as.toString(), st, st.name());
                /*try {
                    //eventBus.post(new NewLogEvent(as.toString(),st.name(),as.getSocket().getInetAddress().getHostAddress(), null, null,null,null,null,null,null,null));
                }catch (Exception e) {
                    LOG.error(e.getMessage());
                }*/
                if(rq != null) {
                    LOG.info("Max OpsInvoked: {}  Max OpsPerformed: {}  Max PDU Length: {}  Number of Pres. Contexts: {}",rq.getMaxOpsInvoked(), rq.getMaxOpsPerformed(), rq.getMaxPDULength(), rq.getNumberOfPresentationContexts());
                }
                if(arg2 != null){
                    LOG.info("UserIdentityAC Length:{}",arg2.length());
                }
                return super.makeAAssociateAC(as, rq, arg2);
            }

            @Override
            protected AAssociateAC negotiate(Association as, AAssociateRQ rq) throws IOException {
                if(as != null) {
                    LOG.info("AAssociateAC negotiate:{}",as.toString());
                }
                return super.negotiate(as, rq);
            }

            @Override
            protected void onClose(Association as) {
                State st = as.getState();
                if(st == State.Sta13){
                    LOG.info("Assocation Released and Closed: {} Name: {}", as.getState(), as.toString());
                   /* try {
                        //eventBus.post(new NewLogEvent(as.toString(),st.name(),as.getSocket().getInetAddress().getHostAddress(), null, null, null, null,null,null,null,null));
                    }  catch (Exception e) {
                        LOG.error(e.getMessage());
                    }*/
                } else  {
                    LOG.info("Association Closed");
                }
                super.onClose(as);
            }
        };
    }
}
