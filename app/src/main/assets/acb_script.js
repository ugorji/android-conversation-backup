var filterCallsTimer = 0;
var filterMessagesTimer = 0;
var debug = true;

function sayhi() {
  alert("Hi");
}

function populateColorKey() {
  var items = [];
  items.push("<table>");
  items.push("<tr><th colspan='2'>Color Key</th></tr>");
  items.push("<tr><th>Call Type</th><th>Message</th></tr>");
  items.push("<tr class='ugorji_call_missed ugorji_message_mms_sender'><td>Missed</td><td>MMS and From Me</td></tr>");
  items.push("<tr class='ugorji_call_incoming ugorji_message_mms'><td>Incoming</td><td>MMS and From Other Person</td></tr>");
  items.push("<tr class='ugorji_call_outgoing ugorji_message_sender'><td>Outgoing</td><td>SMS and From Me</td></tr>");
  items.push("<tr><td>N/A</td><td>SMS and From Other Person</td></tr>");
  items.push("</table>");
  $("#ugorji_color_key").html(items.join(''));
}

function populateSummary(data) {
  if($("#ugorji_summary").hasClass("ugorji_loaded")) return;
  $("#ugorji_summary_title").html("<h2>Run on " + data.datetime + "</h2>");
  var items = [];
  items.push("<table>");
  items.push("<tr><th colspan='2'>Run Parameters</th></tr>");
  $.each(data, function(key, val) {
           items.push("<tr><td>" + key + "</td><td>" + s(val) + "</td></tr>");
         });
  items.push("</table>");
  $("#ugorji_summary").html(items.join(''));
  $("#ugorji_summary").addClass("ugorji_loaded");
}

function populateCallLogs(data) {
  if($("#ugorji_call_logs").hasClass("ugorji_loaded")) return;
  var items = [];
  items.push("<table>");
  items.push("<tr><th>ID</th><th>Name</th><th>Number</th><th>Date</th><th>Duration</th>" + 
             "<th>Type</th></tr>");
  $.each(data.call_logs, function(index, value) { 
           items.push("<tr id='ugorji_call_" + value.id + "' class='");
           if(value.type == 'missed') items.push(" ugorji_call_missed");
           else if(value.type == 'incoming') items.push(" ugorji_call_incoming");
           else if(value.type == 'outgoing') items.push(" ugorji_call_outgoing");
           items.push("'>");
           items.push("<td>" + value.id + 
                      "</td><td id='ugorji_call_name_" + value.id + "'>" + s(value.name) + 
                      "</td><td id='ugorji_call_number_" + value.id + "'>" + s(value.number) + 
                      "</td><td>" + value.datetime + 
                      "</td><td>" + value.duration + 
                      "</td><td>" + value.type + 
                      "</td></tr>"); 
         });
  items.push("</table>");
  $("#ugorji_call_logs").html(items.join(''));
  $("#ugorji_call_logs").addClass("ugorji_loaded");
}

function populateMessages(data) {
  if($("#ugorji_messages").hasClass("ugorji_loaded")) return;
  if(debug) console.log("populateMessages");
  var items = [];
  items.push("<table>");
  items.push("<tr><th>ID</th><th>Name</th><th>Number</th><th>Date</th>" + 
             "<th>Subject</th><th>Text</th><th>Entries</th><th>Type</th></tr>");
  $.each(data.messages, function(index, value) { 
           items.push("<tr id='ugorji_msg_" + value.id + "' class='");
           if(value.sender && value.mms) items.push(" ugorji_message_mms_sender");
           else if(value.sender) items.push(" ugorji_message_sender");
           else if(value.mms) items.push(" ugorji_message_mms");
           else items.push(" ugorji_message");
           items.push("'>");
           items.push("<td>" + value.id + 
                      "</td><td id='ugorji_msg_name_" + value.id + "'>" + s(value.name) + 
                      "</td><td id='ugorji_msg_number_" + value.id + "'>" + s(value.number) + 
                      "</td><td>" + value.datetime + 
                      "</td><td>" + s(value.subject) + 
                      "</td><td>" + s(value.text) + 
                      "</td><td>"); 
           if(value.entries && value.entries.length > 0) {
             $.each(value.entries, function(ix, vx) { 
                      if(vx.filename) items.push(" <a href='mms." + value.id + "." + vx.filename + "'>" + (ix+1) + "</a> ");
                    });
           }
           items.push("</td><td>");
           items.push(value.sender ? "outgoing" : "incoming");
           items.push(value.mms ? " mms" : " sms");
           items.push("</td></tr>");
         });
  items.push("</table>");
  $("#ugorji_messages").html(items.join(''));
  $("#ugorji_messages").addClass("ugorji_loaded");
}

function filterMessages() {
  clearTimeout(filterMessagesTimer);
  filterMessagesTimer = setTimeout(doFilterMessages, 1000);
}

function filterCalls() {
  clearTimeout(filterCallsTimer);
  filterCallsTimer = setTimeout(doFilterCalls, 1000);
}

function doFilterMessages() {
  doFilter("ugorji_messages_filter", "ugorji_messages");
}

function doFilterCalls() {
  doFilter("ugorji_calls_filter", "ugorji_call_logs");
}

function doFilter(filterInputTextId, divId) {
  doFilter2(filterInputTextId, divId);
}

function doFilter1(filterInputTextId, divId) {
  var xRegex = $("#" + filterInputTextId).val();
  if(debug) console.log("doFilter: " + xRegex);
  $("#" + divId  + " tr").show();
  if($.trim(xRegex).length > 0) {
    $("#" + divId  + " tr").not($("#" + divId  + " tr:contains('" + xRegex + "')")).hide();
  }  
}

function doFilter2(filterInputTextId, divId) {
  //show/hide is slow, especially on chrome. so directly set display to none or blank string 
  if(debug) console.log("doFilter2: filterInputTextId: " + filterInputTextId + ", divId: " + divId);
  var s1 = $("#" + filterInputTextId).val();
  s1 = $.trim(s1);
  var s2 = s1.split(/[\s,|]/);
  $("#" + divId  + " tr").each
    (function(index) {
       if(index == 0) {
         $(this).css({'display': ''});
         //$(this).show();
         return;
       }
       var s3 = $(this).text();
       var showme = true;
       if(s1.length > 0) {
         for(var i = 0; i < s2.length; i++) {
           if(s3.indexOf(s2[i]) == -1) {
             showme = false;
             break;
           }
         }
       }
       if(debug && (index % 10 == 0)) console.log("index: " + index + ", showme: " + showme);
       $(this).css({'display': (showme ? '' : 'none')});
       //if(showme) $(this).show();
       //else $(this).hide();
     });
}

function s(x) {
  var y = x;
  if(y == null) y = '';
  return y;
}

function doInit() {
  populateColorKey();
  //var fileExt = ".json";
  //$.getJSON('summary' + fileExt, populateSummary);
  //$.getJSON('messages' + fileExt, populateMessages);
  //$.getJSON('call_logs' + fileExt, populateCallLogs);
  populateSummary(acb_summary);
  populateMessages(acb_messages);
  populateCallLogs(acb_call_logs);
  $("#ugorji_tabs").tabs();
  $("#ugorji_calls_filter").keyup(filterCalls);
  $("#ugorji_messages_filter").keyup(filterMessages);
}

