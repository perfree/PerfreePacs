package com.perfree.dicom.storescu;


import com.perfree.dicom.common.RelatedGeneralSOPClasses;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.imageio.codec.Decompressor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.tool.common.DicomFiles;
import org.dcm4che3.tool.common.DicomFiles.Callback;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class CustomStoreScu {
    private static final Logger LOG = LoggerFactory.getLogger(CustomStoreScu.class);
    private static final ResourceBundle rb = ResourceBundle.getBundle("org.dcm4che3.tool.storescu.messages");
    private final ApplicationEntity ae;
    private final Connection remote = new Connection();
    private final AAssociateRQ rq = new AAssociateRQ();
    private final RelatedGeneralSOPClasses relSOPClasses = new RelatedGeneralSOPClasses();
    private Attributes attrs;
    private String uidSuffix;
    private boolean relExtNeg;
    private int priority;
    private String tmpPrefix = "storescu-";
    private String tmpSuffix;
    private File tmpDir;
    private File tmpFile;
    private Association as;
    private long totalSize;
    private int filesScanned;
    private int filesSent;
    private CustomStoreScu.RSPHandlerFactory rspHandlerFactory = new CustomStoreScu.RSPHandlerFactory() {
        public DimseRSPHandler createDimseRSPHandler(final File f) {
            return new DimseRSPHandler(CustomStoreScu.this.as.nextMessageID()) {
                public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                    CustomStoreScu.this.onCStoreRSP(cmd, f);
                }
            };
        }
    };

    public CustomStoreScu(ApplicationEntity ae) throws IOException {
        this.ae = ae;
        this.rq.addPresentationContext(new PresentationContext(1, "1.2.840.10008.1.1", "1.2.840.10008.1.2"));
    }

    public void setRspHandlerFactory(CustomStoreScu.RSPHandlerFactory rspHandlerFactory) {
        this.rspHandlerFactory = rspHandlerFactory;
    }

    public AAssociateRQ getAAssociateRQ() {
        return this.rq;
    }

    public Connection getRemoteConnection() {
        return this.remote;
    }

    public Attributes getAttributes() {
        return this.attrs;
    }

    public void setAttributes(Attributes attrs) {
        this.attrs = attrs;
    }

    public void setTmpFile(File tmpFile) {
        this.tmpFile = tmpFile;
    }

    public final void setPriority(int priority) {
        this.priority = priority;
    }

    public final void setUIDSuffix(String uidSuffix) {
        this.uidSuffix = uidSuffix;
    }

    public final void setTmpFilePrefix(String prefix) {
        this.tmpPrefix = prefix;
    }

    public final void setTmpFileSuffix(String suffix) {
        this.tmpSuffix = suffix;
    }

    public final void setTmpFileDirectory(File tmpDir) {
        this.tmpDir = tmpDir;
    }

    private static CommandLine parseComandLine(String[] args) throws ParseException {
        Options opts = new Options();
        CLIUtils.addConnectOption(opts);
        CLIUtils.addBindOption(opts, "STORESCU");
        CLIUtils.addAEOptions(opts);
        CLIUtils.addStoreTimeoutOption(opts);
        CLIUtils.addResponseTimeoutOption(opts);
        CLIUtils.addPriorityOption(opts);
        CLIUtils.addCommonOptions(opts);
        addTmpFileOptions(opts);
        addRelatedSOPClassOptions(opts);
        addAttributesOption(opts);
        addUIDSuffixOption(opts);
        return CLIUtils.parseComandLine(args, opts, rb, CustomStoreScu.class);
    }

    private static void addAttributesOption(Options opts) {
        opts.addOption(Option.builder("s").hasArgs().argName("[seq/]attr=value").desc(rb.getString("set")).build());
    }

    public static void addUIDSuffixOption(Options opts) {
        opts.addOption(Option.builder().hasArg().argName("suffix").desc(rb.getString("uid-suffix")).longOpt("uid-suffix").build());
    }

    public static void addTmpFileOptions(Options opts) {
        opts.addOption(Option.builder().hasArg().argName("directory").desc(rb.getString("tmp-file-dir")).longOpt("tmp-file-dir").build());
        opts.addOption(Option.builder().hasArg().argName("prefix").desc(rb.getString("tmp-file-prefix")).longOpt("tmp-file-prefix").build());
        opts.addOption(Option.builder().hasArg().argName("suffix").desc(rb.getString("tmp-file-suffix")).longOpt("tmp-file-suffix").build());
    }

    private static void addRelatedSOPClassOptions(Options opts) {
        opts.addOption((String)null, "rel-ext-neg", false, rb.getString("rel-ext-neg"));
        opts.addOption(Option.builder().hasArg().argName("file|url").desc(rb.getString("rel-sop-classes")).longOpt("rel-sop-classes").build());
    }

    public static void sendDicom(String[] args) throws ParseException, InterruptedException, GeneralSecurityException, IncompatibleConnectionException, IOException {
        try {
            CommandLine cl = parseComandLine(args);
            Device device = new Device("storescu");
            Connection conn = new Connection();
            device.addConnection(conn);
            ApplicationEntity ae = new ApplicationEntity("STORESCU");
            device.addApplicationEntity(ae);
            ae.addConnection(conn);
            CustomStoreScu main = new CustomStoreScu(ae);
            configureTmpFile(main, cl);
            CLIUtils.configureConnect(main.remote, main.rq, cl);
            CLIUtils.configureBind(conn, ae, cl);
            CLIUtils.configure(conn, cl);
            main.remote.setTlsProtocols(conn.getTlsProtocols());
            main.remote.setTlsCipherSuites(conn.getTlsCipherSuites());
            configureRelatedSOPClass(main, cl);
            main.setAttributes(new Attributes());
            CLIUtils.addAttributes(main.attrs, cl.getOptionValues("s"));
            main.setUIDSuffix(cl.getOptionValue("uid-suffix"));
            main.setPriority(CLIUtils.priorityOf(cl));
            List<String> argList = cl.getArgList();
            boolean echo = argList.isEmpty();
            long t1;
            long t2;
            if (!echo) {
                LOG.info(rb.getString("scanning"));
                t1 = System.currentTimeMillis();
                main.scanFiles(argList);
                t2 = System.currentTimeMillis();
                int n = main.filesScanned;
                if (n == 0) {
                    return;
                }

                LOG.info(MessageFormat.format(rb.getString("scanned"), n, (float)(t2 - t1) / 1000.0F, (t2 - t1) / (long)n));
            }

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            device.setExecutor(executorService);
            device.setScheduledExecutor(scheduledExecutorService);

            try {
                t1 = System.currentTimeMillis();
                main.open();
                t2 = System.currentTimeMillis();
                LOG.info(MessageFormat.format(rb.getString("connected"), main.as.getRemoteAET(), t2 - t1));
                if (echo) {
                    main.echo();
                } else {
                    t1 = System.currentTimeMillis();
                    main.sendFiles();
                    t2 = System.currentTimeMillis();
                }
            } finally {
                main.close();
                executorService.shutdown();
                scheduledExecutorService.shutdown();
            }

            if (main.filesScanned > 0) {
                float s = (float)(t2 - t1) / 1000.0F;
                float mb = (float)main.totalSize / 1048576.0F;
                LOG.info(MessageFormat.format(rb.getString("sent"), main.filesSent, mb, s, mb / s));
            }
        } catch (ParseException var20) {
            LOG.error("storescu: " + var20.getMessage());
            LOG.error(rb.getString("try"));
            throw var20;
        } catch (Exception var21) {
            LOG.error("storescu: " + var21.getMessage());
            var21.printStackTrace();
            throw var21;
        }

    }

    public static String uidSuffixOf(CommandLine cl) {
        return cl.getOptionValue("uid-suffix");
    }

    private static void configureTmpFile(CustomStoreScu storescu, CommandLine cl) {
        if (cl.hasOption("tmp-file-dir")) {
            storescu.setTmpFileDirectory(new File(cl.getOptionValue("tmp-file-dir")));
        }

        storescu.setTmpFilePrefix(cl.getOptionValue("tmp-file-prefix", "storescu-"));
        storescu.setTmpFileSuffix(cl.getOptionValue("tmp-file-suffix"));
    }

    public static void configureRelatedSOPClass(CustomStoreScu storescu, CommandLine cl) throws IOException {
        if (cl.hasOption("rel-ext-neg")) {
            storescu.enableSOPClassRelationshipExtNeg(true);
            Properties p = new Properties();
            CLIUtils.loadProperties(cl.hasOption("rel-sop-classes") ? cl.getOptionValue("rel-ext-neg") : "resource:rel-sop-classes.properties", p);
            storescu.relSOPClasses.init(p);
        }

    }

    public final void enableSOPClassRelationshipExtNeg(boolean enable) {
        this.relExtNeg = enable;
    }

    public void scanFiles(List<String> fnames) throws IOException {
        this.scanFiles(fnames, true);
    }

    public void scanFiles(List<String> fnames, boolean printout) throws IOException {
        this.tmpFile = File.createTempFile(this.tmpPrefix, this.tmpSuffix, this.tmpDir);
        this.tmpFile.deleteOnExit();
        final BufferedWriter fileInfos = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.tmpFile)));

        try {
            DicomFiles.scan(fnames, printout, new Callback() {
                public boolean dicomFile(File f, Attributes fmi, long dsPos, Attributes ds) throws IOException {
                    if (!CustomStoreScu.this.addFile(fileInfos, f, dsPos, fmi, ds)) {
                        return false;
                    } else {
                        CustomStoreScu.this.filesScanned++;
                        return true;
                    }
                }
            });
        } finally {
            fileInfos.close();
        }

    }

    public void sendFiles() throws IOException {
        BufferedReader fileInfos = new BufferedReader(new InputStreamReader(new FileInputStream(this.tmpFile)));

        try {
            String line;
            while(this.as.isReadyForDataTransfer() && (line = fileInfos.readLine()) != null) {
                String[] ss = StringUtils.split(line, '\t');

                try {
                    this.send(new File(ss[4]), Long.parseLong(ss[3]), ss[1], ss[0], ss[2]);
                } catch (Exception var10) {
                    var10.printStackTrace();
                }
            }

            try {
                this.as.waitForOutstandingRSP();
            } catch (InterruptedException var9) {
                var9.printStackTrace();
            }
        } finally {
            SafeClose.close(fileInfos);
        }

    }

    public boolean addFile(BufferedWriter fileInfos, File f, long endFmi, Attributes fmi, Attributes ds) throws IOException {
        String cuid = fmi.getString(131074);
        String iuid = fmi.getString(131075);
        String ts = fmi.getString(131088);
        if (cuid != null && iuid != null) {
            fileInfos.write(iuid);
            fileInfos.write(9);
            fileInfos.write(cuid);
            fileInfos.write(9);
            fileInfos.write(ts);
            fileInfos.write(9);
            fileInfos.write(Long.toString(endFmi));
            fileInfos.write(9);
            fileInfos.write(f.getPath());
            fileInfos.newLine();
            if (this.rq.containsPresentationContextFor(cuid, ts)) {
                return true;
            } else {
                if (!this.rq.containsPresentationContextFor(cuid)) {
                    if (this.relExtNeg) {
                        this.rq.addCommonExtendedNegotiation(this.relSOPClasses.getCommonExtendedNegotiation(cuid));
                    }

                    if (!ts.equals("1.2.840.10008.1.2.1")) {
                        this.rq.addPresentationContext(new PresentationContext(this.rq.getNumberOfPresentationContexts() * 2 + 1, cuid, new String[]{"1.2.840.10008.1.2.1"}));
                    }

                    if (!ts.equals("1.2.840.10008.1.2")) {
                        this.rq.addPresentationContext(new PresentationContext(this.rq.getNumberOfPresentationContexts() * 2 + 1, cuid, new String[]{"1.2.840.10008.1.2"}));
                    }
                }

                this.rq.addPresentationContext(new PresentationContext(this.rq.getNumberOfPresentationContexts() * 2 + 1, cuid, new String[]{ts}));
                return true;
            }
        } else {
            return false;
        }
    }

    public void echo() throws IOException, InterruptedException {
        this.as.cecho().next();
    }

    public void send(File f, long fmiEndPos, String cuid, String iuid, String filets) throws IOException, InterruptedException, ParserConfigurationException, SAXException {
        String ts = this.selectTransferSyntax(cuid, filets);
        if (f.getName().endsWith(".xml")) {
            Attributes parsedDicomFile = SAXReader.parse(new FileInputStream(f));
            if (CLIUtils.updateAttributes(parsedDicomFile, this.attrs, this.uidSuffix)) {
                iuid = parsedDicomFile.getString(524312);
            }

            if (!ts.equals(filets)) {
                Decompressor.decompress(parsedDicomFile, filets);
            }

            this.as.cstore(cuid, iuid, this.priority, new DataWriterAdapter(parsedDicomFile), ts, this.rspHandlerFactory.createDimseRSPHandler(f));
        } else if (this.uidSuffix == null && this.attrs.isEmpty() && ts.equals(filets)) {
            FileInputStream in = new FileInputStream(f);

            try {
                in.skip(fmiEndPos);
                InputStreamDataWriter data = new InputStreamDataWriter(in);
                this.as.cstore(cuid, iuid, this.priority, data, ts, this.rspHandlerFactory.createDimseRSPHandler(f));
            } finally {
                SafeClose.close(in);
            }
        } else {
            DicomInputStream in = new DicomInputStream(f);

            try {
                in.setIncludeBulkData(IncludeBulkData.URI);
                Attributes data = in.readDataset();
                if (CLIUtils.updateAttributes(data, this.attrs, this.uidSuffix)) {
                    iuid = data.getString(524312);
                }

                if (!ts.equals(filets)) {
                    Decompressor.decompress(data, filets);
                }

                this.as.cstore(cuid, iuid, this.priority, new DataWriterAdapter(data), ts, this.rspHandlerFactory.createDimseRSPHandler(f));
            } finally {
                SafeClose.close(in);
            }
        }

    }

    private String selectTransferSyntax(String cuid, String filets) {
        Set<String> tss = this.as.getTransferSyntaxesFor(cuid);
        if (tss.contains(filets)) {
            return filets;
        } else {
            return tss.contains("1.2.840.10008.1.2.1") ? "1.2.840.10008.1.2.1" : "1.2.840.10008.1.2";
        }
    }

    public void close() throws IOException, InterruptedException {
        if (this.as != null) {
            if (this.as.isReadyForDataTransfer()) {
                this.as.release();
            }

            this.as.waitForSocketClose();
        }

    }

    public void open() throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        this.as = this.ae.connect(this.remote, this.rq);
    }

    private void onCStoreRSP(Attributes cmd, File f) {
        int status = cmd.getInt(2304, -1);
        switch(status) {
            case 0:
                this.totalSize += f.length();
                ++this.filesSent;
                System.out.print('.');
                break;
            case 45056:
            case 45062:
            case 45063:
                this.totalSize += f.length();
                ++this.filesSent;
                LOG.error(MessageFormat.format(rb.getString("warning"), TagUtils.shortToHexString(status), f));
                LOG.error(cmd.toString());
                break;
            default:
                System.out.print('E');
                LOG.error(MessageFormat.format(rb.getString("error"), TagUtils.shortToHexString(status), f));
                LOG.error(cmd.toString());
        }

    }

    public interface RSPHandlerFactory {
        DimseRSPHandler createDimseRSPHandler(File var1);
    }
}