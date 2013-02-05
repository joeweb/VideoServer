<!--

BigBlueButton - http://www.bigbluebutton.org

Copyright (c) 2008-2009 by respective authors (see below). All rights reserved.

BigBlueButton is free software; you can redistribute it and/or modify it under the 
terms of the GNU Lesser General Public License as published by the Free Software 
Foundation; either version 3 of the License, or (at your option) any later 
version. 

BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY 
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License along 
with BigBlueButton; if not, If not, see <http://www.gnu.org/licenses/>.

Author: Fred Dixon <ffdixon@bigbluebutton.org>

-->
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<% 
	request.setCharacterEncoding("UTF-8"); 
	response.setCharacterEncoding("UTF-8"); 
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Join Selected</title>
</head>
<body>

<%@ include file="bbb_api.jsp"%>

<%
	if (request.getParameterMap().isEmpty()) {
		//
		// Assume we want to create a meeting
		//
%>

<%@ include file="demo_header.jsp"%>

<h2>Join Selected</h2>


<FORM NAME="form1" METHOD="GET">
<table cellpadding="5" cellspacing="5" style="width: 400px; ">
	<tbody>
		<tr>
			<td>
				&nbsp;</td>
			<td style="text-align: right; ">
				Full Name:</td>
			<td style="width: 5px; ">
				&nbsp;</td>
			<td style="text-align: left ">
				<input type="text" autofocus required name="username" /></td>
		</tr>
		
		<tr>
			<td>
				&nbsp;</td>
			<td style="text-align: right; ">
				Course:</td>
			<td>
				&nbsp;
			</td>
			<td style="text-align: left ">
			<select name="meetingID">
				<option value="English 232">English 232</option>
				<option value="English 300">English 300</option>
				<option value="English 402">English 402</option>
				<option value="Demo Meeting">Demo Meeting</option>
			</select>				
			</td>
		</tr>
		<tr>
			<td>
				&nbsp;</td>
			<td>
				&nbsp;</td>
			<td>
				&nbsp;</td>
			<td>
				<input type="submit" value="Join" /></td>
		</tr>	
	</tbody>
</table>
<INPUT TYPE=hidden NAME=action VALUE="create">
</FORM>

<%
	} else if (request.getParameter("action").equals("create")) {
		//
		// Got an action=create
		//

		String username = request.getParameter("username");
		String meetingID = request.getParameter("meetingID");

		// String joinURL = getJoinURL(username, meetingID, "Welcome to " + meetingID );
		// Update: added record parameter, default false
		String url = BigBlueButtonURL.replace("bigbluebutton/","demo/");
		// String preUploadPDF = "<?xml version='1.0' encoding='UTF-8'?><modules><module name='presentation'><document url='"+url+"pdfs/sample.pdf'/></module></modules>";
		// String joinURL = getJoinURL(username, meetingID, "false", "<br>Welcome to course: %%CONFNAME%%.<br>", null, preUploadPDF );
		String joinURL = getJoinURL(username, meetingID, "false", null, null, null );

		if (joinURL.startsWith("http://")) {
%>

<script language="javascript" type="text/javascript">
  window.location.href="<%=joinURL%>";
</script>

<%
	} else {
%>

Error: getJoinURL() failed
<p /><%=joinURL%> <%
 	}
 	}
 %> <%@ include file="demo_footer.jsp"%>
</body>
</html>

