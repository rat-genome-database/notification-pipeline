package edu.mcw.rgd;
import com.sun.mail.smtp.SMTPTransport;
import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.myrgd.MyUser;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Aspect;
import edu.mcw.rgd.process.Utils;
import edu.mcw.rgd.reporting.Link;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.PreparedStatementSetter;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Program to detect changes in RGD objects. The summary emails are then sent to MY_RGD users.
 * <p>
 * cmdline param 'debug=...' allows to test and send all notifications to one debug email
 */
public class NotificationManager {

    Logger log = LogManager.getLogger("updates");

    // if not null, all messages will be sent only to this email account
    String debugEmail = null;

    String footerHtml;

    public static void main(String[] args) throws Exception {

        NotificationManager manager = new NotificationManager();

        for( String arg: args ) {
            if( arg.startsWith("debug=") ) {
                manager.debugEmail = arg.substring(6);
                manager.log.warn("DEBUG MODE! All emails will be sent to "+manager.debugEmail);
            }
        }


        try {

            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy-HH:mm:ss");

            GregorianCalendar gcFrom = new GregorianCalendar();
            GregorianCalendar gcTo = new GregorianCalendar();

            File file = new File(System.getProperty("local.config"));
            FileInputStream fileInput = new FileInputStream(file);
            Properties properties = new Properties();
            properties.load(fileInput);
            fileInput.close();

            if (properties.containsKey("last.run") && properties.getProperty("last.run").length() > 1) {

                Date d = sdf.parse(properties.getProperty("last.run"));
                gcFrom.setTime(d);


            }else {
                gcFrom.add(Calendar.DAY_OF_YEAR, -7);
                //gcFrom.add(Calendar.DAY_OF_YEAR, -765);
            }

            manager.run(gcFrom.getTime(), gcTo.getTime());

            properties.setProperty("last.run", sdf.format(gcTo.getTime()));

            File f = new File(System.getProperty("local.config"));

            OutputStream out = new FileOutputStream( f );
            properties.store(out, "Last Run Date");

            out.close();

        } catch(Exception e) {
            Utils.printStackTrace(e, manager.log);
        }
    }

    public void run(Date from, Date to) throws Exception {

        loadHtmlForFooter();

        SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy");

        GeneDAO gdao = new GeneDAO();

        MyDAO mdao = new MyDAO();
        List<String> users = mdao.getAllWatchedUsers();

        int usersWithNotifications = 0;

        for (String user: users) {

            log.info(user);

            String title = "RGD Update Report: " + format.format(from) + " - " + format.format(to);

            StringBuffer responseMsg = new StringBuffer();

            responseMsg.append("<div style='font-weight:700; font-size:26'>" + title +  "</div>");
            responseMsg.append("<table><tr>");
            responseMsg.append("<td><img style='padding-right:40px' src='http://rgd.mcw.edu/common/images/rgd_LOGO_blue_rgd.gif' border='0'/></td>");
            responseMsg.append("</tr></table>");

            responseMsg.append("<br>");
            responseMsg.append("RGD has retired its legacy login system.  We are now using Google to authenticate.   If you previously used an email address tied to a Google account your login will work as it did before.  If you used an email account that is not registered with Google you have 3 options...");
            responseMsg.append("<ol>");
            responseMsg.append("<li>Create a Google account using the email address previously used to register on RGD.  <a href='https://support.google.com/accounts/answer/27441?hl=en'>https://support.google.com/accounts/answer/27441?hl=en</a></li>");
            responseMsg.append("<li>Use the RGD contact us form to request RGD migrate your notification to an existing Google account.</li>");
            responseMsg.append("<li>Do nothing.  Your notification will continue to be sent even if you do not update your account.   You will not be able to add new notification or modify existing ones.</li>");
            responseMsg.append("</ol>");
            responseMsg.append("<br>If you have question, RGD can be contacted by way of the <a href='http://localhost:8080/rgdweb/contact/contactus.html'>RGD Contact Page</a>");

            boolean foundSomething =false;
            List<WatchedObject> wos = mdao.getWatchedObjects(user);

            for (WatchedObject wo : wos) {

                Gene g = gdao.getGene(wo.getRgdId());
                StringBuffer localMsg = new StringBuffer();

                if (wo.getWatchingNomenclature() ==1) {
                    localMsg.append(checkNomenclature(wo.getRgdId(), from, to));
                }

                if (wo.getWatchingRefseqStatus() ==1 ) {
                    //need to implement
                }

                if (wo.getWatchingProtein() ==1 ) {
                    localMsg.append(checkProteinAndTranscripts(wo.getRgdId(), from, to));
                }

                if (wo.getWatchingInteraction() ==1 ) {
                    localMsg.append(checkInteractions(wo.getRgdId(), from, to));
                }

                if (wo.getWatchingReference() ==1) {
                    localMsg.append(checkReferences(wo.getRgdId(), from, to, 2));
                }
                if (wo.getWatchingDisease() == 1) {
                    localMsg.append(checkAnnotation(wo.getRgdId(), from, to, Aspect.DISEASE));
                }

                if (wo.getWatchingPathway() ==1 ) {
                    localMsg.append(checkAnnotation(wo.getRgdId(), from, to, Aspect.PATHWAY));
                }

                if (wo.getWatchingPhenotype() ==1) {
                    localMsg.append(checkAnnotation(wo.getRgdId(), from, to, Aspect.MAMMALIAN_PHENOTYPE));
                }

                if (wo.getWatchingStrain() ==1 ) {
                    localMsg.append(checkAnnotation(wo.getRgdId(), from, to, Aspect.RAT_STRAIN));
                }

                if (wo.getWatchingGo() ==1) {
                    localMsg.append(checkAnnotation(wo.getRgdId(), from, to, Aspect.BIOLOGICAL_PROCESS));
                    localMsg.append(checkAnnotation(wo.getRgdId(), from, to, Aspect.MOLECULAR_FUNCTION));
                    localMsg.append(checkAnnotation(wo.getRgdId(), from, to, Aspect.CELLULAR_COMPONENT));
                }

               // if (wo.getWatchingAlteredStrains() ==1) {
                    //need to implement
               // }

                if (wo.getWatchingExdb() ==1) {
                    localMsg.append(checkXDBs(wo.getRgdId(), from, to));
                }

                if (localMsg.length() > 10) {
                    foundSomething=true;

                    responseMsg.append("<div style='background-color:#cccccc; margin-top:20px'><span style='font-size:20px;font-weight:700;padding:10px;'>" + g.getSymbol() + "</span> has been updated (<a href='http://rgd.mcw.edu/" + Link.it(wo.getRgdId())  +"'>RGD:" + wo.getRgdId() + "</a>)</div>");
                    responseMsg.append("<table style='margin-left:20px;' border='0'>");
                    responseMsg.append(localMsg);
                    responseMsg.append("</table>");
                }

            }

            OntologyXDAO xdao = new OntologyXDAO();

            List<WatchedTerm> wts = mdao.getWatchedTerms(user);

            for (WatchedTerm wt : wts) {

                StringBuffer localMsg = new StringBuffer();

                if (wt.getWatchingRatGenes() == 1) {
                    localMsg.append(checkGenesAnnotated(wt.getAccId(), from, to, SpeciesType.RAT));
                }
                if (wt.getWatchingMouseGenes() == 1) {
                    localMsg.append(checkGenesAnnotated(wt.getAccId(), from, to, SpeciesType.MOUSE));
                }
                if (wt.getWatchingHumanGenes() == 1) {
                    localMsg.append(checkGenesAnnotated(wt.getAccId(), from, to, SpeciesType.HUMAN));
                }

                if (wt.getWatchingRatQTLS()==1) {
                    localMsg.append(checkQTLsAnnotated(wt.getAccId(), from, to, SpeciesType.RAT));
                }
                if (wt.getWatchingMouseQTLS()==1) {
                    localMsg.append(checkQTLsAnnotated(wt.getAccId(), from, to, SpeciesType.MOUSE));
                }
                if (wt.getWatchingHumanQTLS()==1) {
                    localMsg.append(checkQTLsAnnotated(wt.getAccId(), from, to, SpeciesType.HUMAN));
                }

                if (wt.getWatchingStrains() ==1) {
                    localMsg.append(checkStrainsAnnotated(wt.getAccId(), from, to,SpeciesType.RAT));
                }

                if (wt.getWatchingRatVariants() == 1) {
                    localMsg.append(checkVariantsAnnotated(wt.getAccId(), from, to,SpeciesType.RAT));
                }

                if (localMsg.length() > 10) {
                    foundSomething=true;

                    responseMsg.append("<div style='background-color:#cccccc; margin-top:20px; padding:10px;'><span style='font-size:20px;font-weight:700;'>" + xdao.getTerm(wt.getAccId()).getTerm() + "</span> (<a href='http://rgd.mcw.edu/" + Link.ontView(wt.getAccId())  +"'>" + wt.getAccId() + "</a>)</div>");
                    responseMsg.append("<table style='margin-left:20px;'>");
                    responseMsg.append(localMsg);
                    responseMsg.append("</table>");

                }


            }

            responseMsg.append(footerHtml);

            if (!foundSomething) {
                log.info("   didn't find anything");
            } else {
                // FOUND SOMETHING
                usersWithNotifications++;

                MyUser u =  mdao.getMyUser(user);

                if( debugEmail!=null ) {

                    // DEBUG: override email address
                    if (u.isSendDigest()) {
                        this.send(debugEmail, "DEBUG for "+user+" "+title, responseMsg.toString());
                    }
                    log.info("  # adding to db");
                    mdao.insertMessageCenter(debugEmail, "DEBUG for "+user+" "+title, responseMsg.toString());
                    Thread.sleep(1111); // wait at least 1sec to avoid primary key violations in DB

                } else {

                    // PROD
                    if (u.isSendDigest()) {
                        this.send(user, title, responseMsg.toString());
                    }
                    log.info("  # adding to db");
                    mdao.insertMessageCenter(user, title, responseMsg.toString());
                }
            }

        }


        log.info("===");
        log.info(users.size()+" users processed; messages sent to "+usersWithNotifications+" users");
    }

    // load HTML for email footer
    void loadHtmlForFooter() {

        if( footerHtml==null ) {

            try {
                footerHtml = Utils.readFileAsString("properties/footer.html");

            } catch( IOException e) {
                // default footer: cannot load from file
                footerHtml  = "<br><br><table align='center'><tr><td align='center'>\n";
                footerHtml +="<div id=\"copyright\">\n" +
                        "\t<p>&copy; <a href=\"http://www.mcw.edu/bioinformatics.htm\">Bioinformatics Program, HMGC</a> at the <a href=\"http://www.mcw.edu/\">Medical\n" +
                        "        College of Wisconsin</a></p>\n" +
                        "\t<p align=\"center\">RGD is funded by grant HL64541 from the National Heart, Lung, and Blood Institute on behalf of the NIH.<br><img src=\"http://rgd.mcw.edu/common/images/nhlbilogo.gif\" alt=\"NHLBI Logo\" title=\"National Heart Lung and Blood Institute logo\">\n";

                footerHtml += "<br>Click <a href='https://rgd.mcw.edu/rgdweb/my/login.html'>here</a> to unsubscribe\n";
                footerHtml += "</td></tr></table>\n";
            }
        }
    }

    private String checkAnnotation(int rgdId, Date from, Date to, String aspect) throws Exception {

        AnnotationDAO adao = new AnnotationDAO();
        List<Annotation> annots = adao.getAnnotations(rgdId,from,to, aspect);
        String msg = "";

        if (annots.size() > 0) {

            msg ="<tr><td colspan='3'><div style='background-color:#EFF1F0;margin-top:20px;font-weight:700;'>New " + Aspect.getFriendlyName(aspect) + " Annotations</div></td></tr>";

            HashMap distinct = new HashMap();
            for (Annotation annot : annots) {
                if (distinct.containsKey(annot.getObjectSymbol() + "-" +annot.getTerm() + "-" + annot.getEvidence() )) {
                    continue;
                }else {
                    distinct.put(annot.getObjectSymbol() + "-" +annot.getTerm() + "-" + annot.getEvidence(), null);
                }

                msg += "<tr><td>" + annot.getTerm()+ "</td><td><a href='http://rgd.mcw.edu/rgdweb/report/annotation/main.html?term=" + annot.getTermAcc() + "&id=" + rgdId + "'>" + annot.getTermAcc() + "</a></td><td><span style='padding-left:10px;'>" +  annot.getEvidence() + "</span></td></tr>";
            }
        }

        return msg;
    }

    private String checkNomenclature(int rgdId, Date from, Date to) throws Exception {

        NomenclatureDAO ndao = new NomenclatureDAO();
        List<NomenclatureEvent> events = ndao.getNomenclatureEvents(rgdId,from,to);
        String msg = "";

        if (events.size() > 0) {
            msg = "<tr><td colspan='3'><div style='color: red; margin-top:20px;font-weight:700;'>NOTICE: Nomenclature has changed.</div><td></tr>";

            for (NomenclatureEvent event: events) {
                msg += "<tr><td>"+ event.getDesc() + "</td></tr>";

            }

        }

        return msg;
    }

    private String checkXDBs(int rgdId, Date from, Date to) throws Exception {

        ArrayList xdbIds = new ArrayList();
        xdbIds.add(20); //ensembl genes
        xdbIds.add(27); //Ensembl Protien
        xdbIds.add(30); //pfam
        xdbIds.add(3);
        xdbIds.add(7);
        xdbIds.add(10);
        xdbIds.add(14);
        xdbIds.add(42);
        xdbIds.add(60);
        xdbIds.add(2);



        XdbIdDAO xdao = new XdbIdDAO();
        List<XdbId> ids = xdao.getXdbIdsWithExclusion(rgdId, from, to, xdbIds);
        String msg = "";

        if (ids.size() > 0) {

            msg += "<tr><td colspan='3'><div style='background-color:#EFF1F0;margin-top:20px;font-weight:700;'>Updates from External Data Providers</div></td></tr>";

            for (XdbId id: ids) {

                if (id.getXdbKey()!=2) {
                    msg += "<tr><td>" + id.getXdbKeyAsString() + "</td><td>" + id.getAccId() + "</td><td>" + id.getSrcPipeline() + "</td></tr>";
                }
            }

        }

        return msg;
    }

    private String checkInteractions(int rgdId, Date from, Date to) throws Exception {


        InteractionsDAO idao = new InteractionsDAO();


        AssociationDAO adao = new AssociationDAO();

        List<Association> associations = adao.getAssociationsForDetailRgdId(rgdId,"protein_to_gene");

        ArrayList<Integer> proteinList = new ArrayList<Integer>();

        for (Association a: associations) {
            proteinList.add(a.getMasterRgdId());
        }

        List<Interaction> interactions = idao.getInteractionsByRgdIdsList(proteinList,from,to);

        String msg = "";


        ProteinDAO pdao = new ProteinDAO();

        OntologyXDAO odao = new OntologyXDAO();




        if (interactions.size() > 0) {

            msg += "<tr><td colspan='3'><div style='background-color:#EFF1F0;margin-top:20px;font-weight:700;'>New Protein Interactions Found</div></td></tr>";

            for (Interaction interaction: interactions) {
                msg += "<tr><td>" + odao.getTermByAccId(interaction.getInteractionType()).getTerm() + " : " +  pdao.getProtein(interaction.getRgdId1()).getUniprotId() + " > " +  pdao.getProtein(interaction.getRgdId2()).getUniprotId() + "</td></tr>";

            }

        }

        return msg;
    }




    private String checkProteinAndTranscripts(int rgdId, Date from, Date to) throws Exception {

        ArrayList xdbIds = new ArrayList();

        xdbIds.add(20); //ensembl genes
        xdbIds.add(27); //Ensembl Protien
        xdbIds.add(30); //pfam
        xdbIds.add(3);
        xdbIds.add(7);
        xdbIds.add(10);
        xdbIds.add(14);
        xdbIds.add(42);
        xdbIds.add(60);


        XdbIdDAO xdao = new XdbIdDAO();
        List<XdbId> ids = xdao.getXdbIds(rgdId, from, to, xdbIds);
        String msg = "";

        if (ids.size() > 0) {

            msg += "<tr><td colspan='3'><div style='background-color:#EFF1F0;margin-top:20px;font-weight:700;'>Updates to Protein and Transcript Information</div></td></tr>";

            for (XdbId id: ids) {

                if (id.getXdbKey()!=2) {
                    msg += "<tr><td>" + id.getAccId() + "</td><td>" + id.getXdbKeyAsString() + "</td><td>" + id.getSrcPipeline() + "</td></tr>";
                }
            }

        }

        return msg;
    }



    private String checkReferences(int rgdId, Date from, Date to, int xdbKey) throws Exception {

        XdbIdDAO xdao = new XdbIdDAO();
        List<XdbId> ids = xdao.getXdbIds(rgdId, from, to, xdbKey);
        String msg ="";
        ReferenceDAO rdao = new ReferenceDAO();
        if (ids.size() > 0) {

           msg = "<tr><td colspan='3'><div style='background-color:#EFF1F0;margin-top:20px;font-weight:700;'>New PubMed Reference Associations (via PubMed)</td></tr>";

            for (XdbId id: ids) {

                Reference r = rdao.getReferenceByPubmedId(id.getAccId());

                if (r != null) {
                    msg += "<tr><td colspan='3'><a  href='https://rgd.mcw.edu/"+Link.ref(r.getRgdId())+"'>" + r.getTitle() + "</a>, PMID:"+id.getAccId()+"</td></tr>";
                    msg += "<tr><td colspan='3'><div style='margin-bottom:5px;'>" + r.getCitation() + "</div></td></tr>";

                }else {
                    msg += "<tr><td colspan='3'><div style='margin-bottom:5px;'>PubMed:<a href='http://www.ncbi.nlm.nih.gov/pubmed/" + id.getAccId() + "'>" + id.getAccId() + "</a></div></td></tr>";
                }
            }

        }

        return msg;
    }

    private String checkGenesAnnotated(String termAcc, Date from, Date to, int speciesTypeKey) throws Exception {

        AnnotationDAO adao = new AnnotationDAO();
        List<Annotation> annots = adao.getAnnotations(termAcc,1,from, to, speciesTypeKey);
        String msg = "";

        if (annots.size() > 0) {

            HashMap distinct = new HashMap();

            msg +="<tr><td>&nbsp;</td></tr><tr><td colspan='4' style='background-color:#EFF1F0'><b>New Gene Annotations (" + SpeciesType.getTaxonomicName(speciesTypeKey) + ")</b></td></tr>";
            for (Annotation annot : annots) {

                if (distinct.containsKey(annot.getObjectSymbol() + "-" +annot.getTerm() + "-" + annot.getEvidence() )) {
                    continue;
                }else {
                    distinct.put(annot.getObjectSymbol() + "-" +annot.getTerm() + "-" + annot.getEvidence(), null);
                }

                msg += "<tr>";

                //msg +="<td>" + annot.getTerm() + "</td>";
                msg +="<td>" + annot.getObjectSymbol() + "</td>";
                msg +="<td>" + annot.getTerm() + "</td>";
                msg +="<td><a href='http://rgd.mcw.edu" + Link.it(annot.getAnnotatedObjectRgdId()) + "'>RGD:" + annot.getAnnotatedObjectRgdId() + "</a></td>";
                msg +="<td><span style='padding-left:10px;'>" + annot.getEvidence() + "</span></td>";
                msg += "</tr>";


                // msg += "\t" + annot.getAnnotatedObjectRgdId() + ":" + annot.getObjectSymbol() + ":" + annot.getObjectName() + "<br>\n";
            }
        }

        return msg;
    }

    private String checkQTLsAnnotated(String termAcc, Date from, Date to, int speciesTypeKey) throws Exception {

        AnnotationDAO adao = new AnnotationDAO();
        List<Annotation> annots = adao.getAnnotations(termAcc,6,from,to,speciesTypeKey);
        String msg = "";

        if (annots.size() > 0) {

            msg +="<tr><td>&nbsp;</td></tr><tr><td colspan='4' style='background-color:#EFF1F0'><b>New QTL Annotations (" + SpeciesType.getTaxonomicName(speciesTypeKey) + ")</b></td></tr>";
            HashMap distinct= new HashMap();
            for (Annotation annot : annots) {

                if (distinct.containsKey(annot.getObjectSymbol() + "-" +annot.getTerm() + "-" + annot.getEvidence() )) {
                    continue;
                }else {
                    distinct.put(annot.getObjectSymbol() + "-" +annot.getTerm() + "-" + annot.getEvidence(), null);
                }


                msg += "<tr>";

                //msg +="<td>" + annot.getTerm() + "</td>";
                msg +="<td>" + annot.getObjectSymbol() + "</td>";
                msg +="<td>" + annot.getTerm() + "</td>";
                msg +="<td><a href='http://rgd.mcw.edu" + Link.it(annot.getAnnotatedObjectRgdId()) + "'>RGD:" + annot.getAnnotatedObjectRgdId() + "</a></td>";
                msg +="<td><span style='padding-left:10px;'>" + annot.getEvidence() + "</span></td>";

                msg += "</tr>";


                // msg += "\t" + annot.getAnnotatedObjectRgdId() + ":" + annot.getObjectSymbol() + ":" + annot.getObjectName() + "<br>\n";
            }
        }

        return msg;
    }

    private String checkStrainsAnnotated(String termAcc, Date from, Date to, int speciesTypeKey) throws Exception {

        AnnotationDAO adao = new AnnotationDAO();
        List<Annotation> annots = adao.getAnnotations(termAcc,5,from,to,speciesTypeKey);
        String msg = "";

        if (annots.size() > 0) {

            msg +="<tr><td>&nbsp;</td></tr><tr><td colspan='4' style='background-color:#EFF1F0'><b>New Strain Annotations (" + SpeciesType.getTaxonomicName(speciesTypeKey) + ")</b></td></tr>";
            HashMap distinct= new HashMap();
            for (Annotation annot : annots) {

                if (distinct.containsKey(annot.getObjectSymbol() + "-" +annot.getTerm() + "-" + annot.getEvidence() )) {
                    continue;
                }else {
                    distinct.put(annot.getObjectSymbol() + "-" +annot.getTerm() + "-" + annot.getEvidence(), null);
                }

                msg += "<tr>";

                String name = annot.getObjectName();
                if (annot.getObjectName() == null) {
                    name ="---";
                }

                //msg +="<td>" + annot.getTerm() + "</td>";
                msg +="<td>" + annot.getObjectSymbol() + "</td>";
                msg +="<td>" + annot.getTerm() + "</td>";
                msg +="<td><a href='http://rgd.mcw.edu" + Link.it(annot.getAnnotatedObjectRgdId()) + "'>RGD:" + annot.getAnnotatedObjectRgdId() + "</a></td>";
                msg +="<td><span style='padding-left:10px;'>" + annot.getEvidence() + "</span></td>";

                msg += "</tr>";


                // msg += "\t" + annot.getAnnotatedObjectRgdId() + ":" + annot.getObjectSymbol() + ":" + annot.getObjectName() + "<br>\n";
            }
        }

        return msg;
    }

    private String checkVariantsAnnotated(String termAcc, Date from, Date to, int speciesTypeKey) throws Exception {

        AnnotationDAO adao = new AnnotationDAO();
        List<Annotation> annots = adao.getAnnotations(termAcc,7,from,to,speciesTypeKey);
        String msg = "";

        if (annots.size() > 0) {

            msg +="<tr><td>&nbsp;</td></tr><tr><td colspan='4' style='background-color:#EFF1F0'><b>New Variant Annotations (" + SpeciesType.getTaxonomicName(speciesTypeKey) + ")</b></td></tr>";
            HashMap distinct = new HashMap();
            for (Annotation annot : annots) {

                if (distinct.containsKey(annot.getObjectSymbol() + "-" +annot.getTerm() + "-" + annot.getEvidence() )) {
                    continue;
                }else {
                    distinct.put(annot.getObjectSymbol() + "-" +annot.getTerm() + "-" + annot.getEvidence(), null);
                }

                msg += "<tr>";

                //msg +="<td>" + annot.getTerm() + "</td>";
                msg +="<td>" + annot.getObjectName() + "</td>";
                msg +="<td>" + annot.getTerm() + "</td>";
                msg +="<td><a href='http://rgd.mcw.edu" + Link.it(annot.getAnnotatedObjectRgdId()) + "'>RGD:" + annot.getAnnotatedObjectRgdId() + "</a></td>";
                msg +="<td><span style='padding-left:10px;'>" + annot.getEvidence() + "</span></td>";

                msg += "</tr>";


                // msg += "\t" + annot.getAnnotatedObjectRgdId() + ":" + annot.getObjectSymbol() + ":" + annot.getObjectName() + "<br>\n";
            }
        }

        return msg;
    }


    public static void send(String recipientEmail, String title, String message) throws Exception{

        // Get a Properties object
        Properties props = System.getProperties();

        props.setProperty("mail.smtp.host", "smtp.mcw.edu");
        props.setProperty("mail.smtp.port", "25");

        Session session = Session.getInstance(props, null);

        // -- Create a new message --
        final MimeMessage msg = new MimeMessage(session);

        // -- Set the FROM and TO fields --
        msg.setFrom(new InternetAddress("rgd@mcw.edu", "Rat Genome Database"));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail, false));

        //msg.setRecipients(Message.RecipientType.BCC, InternetAddress.parse("jdepons@mcw.edu", false));

        msg.setSubject(title);
        //msg.setText(message, "utf-8");
        msg.setContent(message, "text/html");

        msg.setSentDate(new Date());

        SMTPTransport t = (SMTPTransport)session.getTransport("smtp");

        t.connect();
        t.sendMessage(msg, msg.getAllRecipients());
        t.close();
    }

}
