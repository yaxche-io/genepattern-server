<!-- /*
  The Broad Institute
  SOFTWARE COPYRIGHT NOTICE AGREEMENT
  This software and its documentation are copyright (2003-2006) by the
  Broad Institute/Massachusetts Institute of Technology. All rights are
  reserved.

  This software is supplied without any warranty or guaranteed support
  whatsoever. Neither the Broad Institute nor MIT can be responsible for its
  use, misuse, or functionality.
*/ -->


<%@ page import="java.io.IOException,
		 java.util.StringTokenizer,
		 java.util.Enumeration,
		 java.util.HashMap,
		 java.io.File,
		 java.net.URL,
		 java.net.MalformedURLException,
		 java.util.Date,
		 java.io.UnsupportedEncodingException,
		 java.net.InetAddress,
 		 java.net.URLEncoder, 
		 java.net.URLDecoder,
 		 java.text.SimpleDateFormat,
		 java.util.Date,
		 java.util.Enumeration, 
		 java.util.ArrayList, 
		 java.util.List,
		 java.util.Iterator,
		 java.util.GregorianCalendar,
		 java.text.ParseException,
		 java.text.DateFormat,
		
		 org.genepattern.webservice.TaskInfo,
		 org.genepattern.webservice.TaskInfoAttributes,
 		 org.genepattern.util.StringUtils,
		 org.genepattern.webservice.ParameterFormatConverter,
		 org.genepattern.webservice.ParameterInfo,
		 org.genepattern.server.genepattern.GenePatternAnalysisTask,
		 org.genepattern.util.GPConstants,
		 org.genepattern.webservice.OmnigeneException,
		 org.genepattern.webservice.AnalysisWebServiceProxy,
		 org.genepattern.webservice.TaskInfo,
		 org.genepattern.webservice.JobInfo,
		 org.apache.commons.fileupload.servlet.*,
		 org.apache.commons.fileupload.*,
		 org.apache.commons.fileupload.disk.DiskFileItemFactory,
		 org.genepattern.data.pipeline.PipelineModel"
	session="false" contentType="text/html" language="Java" %>
<%
response.setHeader("Cache-Control", "no-store"); // HTTP 1.1 cache control
response.setHeader("Pragma", "no-cache");		 // HTTP 1.0 cache control
response.setDateHeader("Expires", 0);
%>
<html>
<head>
<link href="skin/stylesheet.css" rel="stylesheet" type="text/css">
	<link href="skin/favicon.ico" rel="shortcut icon">
	<title>Running Task</title>
</head>
<body>
	
<jsp:include page="navbar.jsp"></jsp:include>

<%

String userID =  null;


try {
    ServletFileUpload fub = new ServletFileUpload(new DiskFileItemFactory());
    List params = fub.parseRequest(request);
    
	HashMap htFilenames = new HashMap(); // map between form field name and filesystem name

	// create a dir for the input files
	File tempDir = File.createTempFile("runtask", ".gp");
	tempDir.delete();
	tempDir.mkdir();
	String tmpDirName = tempDir.getName();
	HashMap requestParameters = new HashMap();
	
	HashMap nameToFileItemMap = new HashMap();
	for (Iterator iter = params.iterator(); iter.hasNext();) {
	    FileItem fi = (FileItem) iter.next();
	    nameToFileItemMap.put(fi.getFieldName(), fi);
	}
	
    int fileCount = 0;
	for (Iterator iter = params.iterator(); iter.hasNext();) {
         FileItem fi = (FileItem) iter.next();
         if (!fi.isFormField()) {
             
             String fieldName = fi.getFieldName();
             String fileName = fi.getName();
             if (fileName == null || fileName.trim().equals("")) {
                 FileItem shadow = (FileItem) nameToFileItemMap.get("shadow" + fileCount);
              	  if(shadow!=null) {
              	  		fileName = shadow.getString();
              	  }
             }
             fileCount++;
			if(fileName != null && !fileName.trim().equals("")) {
	    			try {
	    			    new URL(fileName);
	    			    // don't bother trying to save a file that is a URL, retrieve it at execution time instead
	    				htFilenames.put(fieldName, fileName);    
	    			} catch(MalformedURLException mfe) {
	    			    fileName = org.apache.commons.io.FilenameUtils.getName(fileName);
	    			    File file = new File(tempDir, fileName);
	    			    fi.write(file);
	    			    htFilenames.put(fieldName, file.getCanonicalPath()); 
	    			}
            }
         } else {
             requestParameters.put(fi.getFieldName(), fi.getString());
         }

	} // loop over files

	
	String lsid = (String) requestParameters.get("taskLSID");
	String taskName = (String) requestParameters.get("taskName");
	if(lsid==null) {
	    lsid = taskName;   
	}
	userID = (String) requestParameters.get(GPConstants.USERID);
	// set up the call to the analysis engine
 	String server = request.getScheme() + "://"+ InetAddress.getLocalHost().getCanonicalHostName() + ":"
					+ System.getProperty("GENEPATTERN_PORT");

	AnalysisWebServiceProxy analysisProxy = new AnalysisWebServiceProxy(server, userID);
	
	TaskInfo task = GenePatternAnalysisTask.getTaskInfo(lsid, userID);
	if(task==null) {
	 	out.println("Unable to find task " + lsid);   
	 	return;
	}
	ParameterInfo[] parmInfos = task.getParameterInfoArray();
	
	int nParams = 0;
	if (parmInfos != null){ 
		nParams = parmInfos.length;
	} else {
		parmInfos = new ParameterInfo[0];
	}
	ArrayList missingReqParams = new ArrayList ();
	for (int i=0; i < nParams; i++){
		ParameterInfo pinfo = parmInfos[i];
		String value;	
		if (pinfo.isInputFile()){
			value = (String)htFilenames.get(pinfo.getName());
			if (value == null) {
				value = "";
				pinfo.getAttributes().put(ParameterInfo.TYPE, "");
			}
			try {
			    new URL(value);
			    HashMap attrs = pinfo.getAttributes();
				attrs.put(pinfo.MODE , pinfo.URL_INPUT_MODE);
				attrs.remove(pinfo.TYPE);
			} catch(MalformedURLException mfe) {}
			
		} else {
			value = (String) requestParameters.get(pinfo.getName());
		}

		//
		// look for missing required params
		//
		if ((value == null) || (value.trim().length() == 0)){
			HashMap pia = pinfo.getAttributes();
			boolean isOptional = ((String)pia.get(GPConstants.PARAM_INFO_OPTIONAL[GPConstants.PARAM_INFO_NAME_OFFSET])).length() > 0;
		
			if (!isOptional){
				missingReqParams.add(pinfo);
			}
		}
		pinfo.setValue(value);
	}
	if (missingReqParams.size() > 0){
		System.out.println(""+missingReqParams);
		request.setAttribute("missingReqParams", missingReqParams);
		(request.getRequestDispatcher("runTaskMissingParams.jsp")).include(request, response);
%>
		<jsp:include page="footer.jsp"></jsp:include>
		</body>
		</html>
<%
		return;
	}

	JobInfo job = analysisProxy.submitJob(task.getID(), parmInfos);
	String jobID = ""+job.getJobNumber();
 
%>
<script language="Javascript">

var pipelineStopped = false;

function stopPipeline(button) {
	var really = confirm('Really stop the pipeline?');
	if (!really) return;
	window.open("runPipeline.jsp?cmd=stop&jobID=<%= job.getJobNumber() %>", "_blank", "height=100, width=100, directories=no, menubar=no, statusbar=no, resizable=no");
	pipelineStopped = true;
}
function checkAll(frm, bChecked) {
	frm = document.forms["results"];
	for (i = 0; i < frm.elements.length; i++) {
		if (frm.elements[i].type != "checkbox") continue;
		frm.elements[i].checked = bChecked;
	}
}


</script>


<form name="frmstop">
		<input name="cmd" type="button" value="stop..." onclick="stopPipeline(this)" class="little">
		<input type="hidden" name="jobID" value="<%= job.getJobNumber()%>">
</form>
<form name="frmemail" method="POST" target="_blank" action="sendMail.jsp" onsubmit="javascript:return false;">
		email notification to: <input name="to" class="little" size="70" value="" onkeydown="return suppressEnterKey(event)">
		<input type="hidden" name="from" value="<%= StringUtils.htmlEncode(userID) %>">
		<input type="hidden" name="subject" value="<%= task.getName() %> results for job # <%= jobID %>">
		<input type="hidden" name="message" value="<html><head><link href='stylesheet.css' rel='stylesheet' type='text/css'><script language='Javascript'>\nfunction checkAll(frm, bChecked) {\n\tfrm = document.forms['results'];\n\tfor (i = 0; i < frm.elements.length; i++) {\n\t\tif (frm.elements[i].type != 'checkbox') continue; \n\t\tfrm.elements[i].checked = bChecked;\n\t}\n}\n</script></head><body>">
</form>

<table width='100%' cellpadding='10'>
<tr><td>
Running <a href="addTask.jsp?view=1&name=<%=requestParameters.get("taskLSID")%>"><%=requestParameters.get("taskName")%></a> as job # <a href="getJobResults.jsp?jobID=<%=job.getJobNumber() %>"><%=job.getJobNumber() %></a> on <%=new Date()%> 
				
</tr></td>
<tr><td>
<%=requestParameters.get("taskName")%> ( 
<%


ParameterInfo[] formalParameterInfoArray = null;
try {
        formalParameterInfoArray  = new ParameterFormatConverter().getParameterInfoArray(task.getParameterInfo());
	if (formalParameterInfoArray == null) formalParameterInfoArray = new ParameterInfo[0];
} catch (OmnigeneException oe) {
}



for (int i=0; i < parmInfos.length; i++){

		ParameterInfo pinfo = parmInfos[i];
		ParameterInfo formalPinfo = pinfo;
		for (int j=0; j < formalParameterInfoArray.length; j++){
			ParameterInfo pi = formalParameterInfoArray[j];
			if (pi.getName().equals(pinfo.getName())){
				formalPinfo = pi;
				break;
			}
		}

		String value = pinfo.getValue();	
		boolean isURL = false;
		try {
		     new URL(value);
		     isURL = true;
		} catch(MalformedURLException mfe) {}
		
		out.println(pinfo.getName().replace('.',' '));
		out.println("=");
		if (pinfo.isInputFile()) {
			String htmlValue = StringUtils.htmlEncode(pinfo.getValue().trim());	
			
			if (isURL) {
				out.println("<a href='"+ htmlValue + "'>"+htmlValue +"</a>");
			} else {
				out.println("<a href='getFile.jsp?task=&file="+ URLEncoder.encode(tmpDirName +"/" + value)+"'>"+htmlValue +"</a>");

			}
		} else if (isURL) {
			out.println("<a href='"+ value + "'>"+value +"</a>");

		} else {
			String display = pinfo.getUIValue(formalPinfo);
			out.println(StringUtils.htmlEncode(display));
		}
		if (i != (parmInfos.length -1))out.println(", ");
	}
%>
)<br></td></tr>

<form name="results" action="zipJobResults.jsp">
<input type="hidden" name="name" value="<%=task.getName()%>" />
<input type="hidden" name="jobID" value="<%=jobID%>" />


<%
	for (int i = 0; i < 8*1024; i++) out.print(" ");
	out.println();
	out.flush();

	String status = "started";
	while (!(status.equalsIgnoreCase("ERROR") || (status
				.equalsIgnoreCase("Finished")))) {
		Thread.currentThread().sleep(500);
		job = analysisProxy.checkStatus(job.getJobNumber());
		status = job.getStatus();
	}
	

	// after task completes
	JobInfo jobInfo = job; 

	ParameterInfo[] jobParams = jobInfo.getParameterInfoArray();
	StringBuffer sbOut = new StringBuffer();

	for (int j = 0; j < jobParams.length; j++) {
		if (!jobParams[j].isOutputFile()) {
			continue;
		}
		sbOut.setLength(0);
		String fileName = new File("../../" + jobParams[j].getValue())
			.getName();
		sbOut.append("<tr><td><input type=\"checkbox\" value=\"");
		sbOut.append("NAME" + "/" + fileName + "=" + jobInfo.getJobNumber()
				+ "/" + fileName);
		sbOut.append("\" name=\"dl\" ");
		sbOut.append("checked><a target=\"_blank\" href=\"");
			String outFileUrl = null;
		try {
			outFileUrl = "retrieveResults.jsp?job="
					+ jobInfo.getJobNumber() + "&filename="
					+ URLEncoder.encode(fileName, "utf-8");
		} catch (UnsupportedEncodingException uee) {
			outFileUrl = "retrieveResults.jsp?job="
					+ jobInfo.getJobNumber() + "&filename=" + fileName;
		}
			sbOut.append(outFileUrl);
		try {
			fileName = URLDecoder.decode(fileName, "UTF-8");
		} catch (UnsupportedEncodingException uee) {
			// ignore
		}
		sbOut.append("\">" + StringUtils.htmlEncode(fileName) + "</a></td></tr>");
		out.println(sbOut.toString());
	}
	out.flush();

	out.println("<tr><td><center><input type=\"submit\" name=\"download\" value=\"download selected results\">&nbsp;&nbsp;");
	out.println("<a href=\"javascript:checkAll(this.form, true)\">check all</a> &nbsp;&nbsp;");
	out.println("<a href=\"javascript:checkAll(this.form, false)\">uncheck all</a></center><br><center>");
	out.println("<input type=\"submit\" name=\"delete\" value=\"delete selected results\"");
	out.println(" onclick=\"return confirm(\'Really delete the selected files?\')\">");
	out.println("</form></td></tr>");


%>

</table>
<script language="Javascript">

			document.frmstop.cmd.disabled = true;
			document.frmstop.cmd.visibility = false;
			var frm = document.frmemail;
			frm.to.readonly = true; // no more edits as it is about to be used for addressing
			var to = frm.to.value;
			if (to != "") {
				frm.message.value = frm.message.value + 'job <%=jobID%> completed';
				frm.submit();
			}
		</script>
<%
		GregorianCalendar purgeTOD = new GregorianCalendar();
		
		try {
                    
			SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
			GregorianCalendar gcPurge = new GregorianCalendar();
			gcPurge.setTime(dateFormat.parse(System.getProperty("purgeTime", "23:00")));
			purgeTOD.set(GregorianCalendar.HOUR_OF_DAY, gcPurge.get(GregorianCalendar.HOUR_OF_DAY));
			purgeTOD.set(GregorianCalendar.MINUTE, gcPurge.get(GregorianCalendar.MINUTE));
		} catch (ParseException pe) {
			purgeTOD.set(GregorianCalendar.HOUR_OF_DAY, 23);
			purgeTOD.set(GregorianCalendar.MINUTE, 0);
		}
		purgeTOD.set(GregorianCalendar.SECOND, 0);
		purgeTOD.set(GregorianCalendar.MILLISECOND, 0);
		int purgeInterval;
		try {
			purgeInterval = Integer.parseInt(System.getProperty("purgeJobsAfter", "-1"));
		} catch (NumberFormatException nfe) {
			purgeInterval  = 7;
		}
		purgeTOD.add(GregorianCalendar.DATE, purgeInterval);
		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
%>
</center>
<br>
	These job results are scheduled to be purged from the server on <%= df.format(purgeTOD.getTime()).toLowerCase() %><br>

<%
} catch (Throwable e){
    e.printStackTrace();
    out.println("An error occurred.");
}
%>
<jsp:include page="footer.jsp"></jsp:include>

</body>
</html>

