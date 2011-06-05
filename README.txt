WHAT IT DOES
============

Backup conversations (call logs, sms and mms messages for all, or specific numbers). It backs up the messages into a zip file containing 
- call_logs.json
- messages.json
- other files with format mms.<messageid>.<filename>
- index.html (which reads up the json files, and allows you reference everything

UI is simple, with only one activity, that gives prompt for
- backup all conversations or specify numbers to backup for
- what to backup: call logs, messages, MMS attachments???
- should we delete backed up items, and should we email u the archive later?

CONCERNS
========
This is a major hack, because google doesn't provide public API's for accessing SMS and MMS.

I had to reverse-engineer the actual android src to know what to do. Thus, this is only guaranteed to work on Android 2.3 (which is what I tested this on).

Tips:
========
telnet localhost 5554 <emulator_port>
# from here, use the following:
sms send 5555555555 hello there are you ok?
sms send 5555555555 finally a response
sms send 9999999999 thank you
gsm call 5555555555
gsm call 5555555555
gsm call 9999999999

Within eclipse, use DDMS (from window --> show perspective --> other --> DDMS) to see files on sd card, and transfer files to and from the card.

Unfortunately, emulator does not test MMS, so we have to test it with a real device (bummer).

Some things TBD
================
- DISCRIMINATOR_COLUMN throwing exception. I had to decipher mms/sms a different way (by looking for contents of an mms-only column)
- MMS has not been tested. (not tested ... only in real device)
- Support for onclick listener (for "backup all") - DONE
- support delete - DONE
- support email backup - DONE (not tested ... only in real device)

Note that, for conversations involving more than just one person, they may be deleted when a deletion is done. This is because we now use the thread_ids to delete conversations (since that's the only way we could get things to work).

Next time, consider using PreferencesManager:
http://www.ibm.com/developerworks/xml/library/x-androidstorage/?ca=drs-

errors:
- it only gets sms for some (22 for 484--- number and call logs for same)
  - also, only got my outgoing sms for 646-686-... skipping incoming, and other messages
  - also skipped all the MMS attachments
- on widescreen, it tries to show dialog again
- on widescreen, showing alertdialog bombs

Fixes:
- when I quote the numbers, then it works better
  - solution: tokenize the numbers on (space , " ') into a set, remove the blanks, then output them correctly
- Call Logs: sometimes, only the first one goes through
  # Problem is that some numbers in db are 1XXXXXXXXX, some +1XXXXXXXXXX
  # Is there a way to search using canonical numbers???
  Fixed by using Where ... like ... (instead of Where ... IN ...), and without +countrycode part of number (e.g. use 4445556666 TO MATCH 4445556666 or 14445556666, or +14445556666)
- : however, we still don't get the MMS, and we also do not get the call logs???

Only thing left to do is get the attachments
- then remove code that always skips deleting after
problem:
- MMS has no address, so we need to rather use thread_ids to find the messages
  - ie use addresses to find the thread_ids conversations tables (in a Set<Long>)
  - search again using thread_ids to find all messages in those conversations
    - for each message
      - if mms, then 
        - get mms addresses from mms/addr (to set the Ms.sender and Ms.address fields)
          (if i'm sender, then address is in FROM field; else address is in TO, CC or BCC field)
          PduHeaders.BCC, PduHeaders.CC, PduHeaders.FROM, PduHeaders.TO
        - get mms parts from mms/part (to set the Ms.entries fields)
          
mms table has some thread_id, msg_id
mms/addr has for each msg_id, multiple rows with addresses (type FROM, TO, etc)
mms/part has for each msg_id, multiple rows with part contents (each part has a text and a data section)

skip SMIL (any string matching <smil> ... </smil> ignore case is skipped)
number is acting up: 
- when someone sends something, it shows my number (should show theirs)
- when i send something, it shows insert-address-token

Call logs are not all showing up (only a subset of them)
- it's correct

Timestamp for mms is wrong
- need to multipy by 1000, since they use seconds (not milliseconds like sms)

Tested my app on version 2.1 and above, and works well on emulator
- didn't test on actual device ... 
- this theoritically covers 90% of android phones (source: http://en.wikipedia.org/wiki/Android_(operating_system))
For release
- put strings into strings.xml (actually, do that later) DONE
- remove SAFETY checks (in Helper.java)
- test backing up everything on my phone ... see if it works
  ie all call logs, sms and mms
  (Tested: works fine)
- Need to create simple html file with javascript, to load up json files and show in browser (for easy viewing)
  - see all json files in there (listed by name)
  - you select one, and it shows that one
  - Link at top takes u back to listing

App is simple:
- No synchronization
- No prettiness in the html

Files being used
================
~/depot/android-conversation-backup/README.txt
~/depot/android-conversation-backup/res/values/strings.xml
~/depot/android-conversation-backup/src/net/ugorji/android/conversationbackup/ProcessingService.java
~/depot/android-conversation-backup/src/net/ugorji/android/conversationbackup/HomeActivity.java
 ~/depot/android-conversation-backup/src/net/ugorji/android/conversationbackup/Helper.java
~/depot/android-conversation-backup/AndroidManifest.xml
~/Documents/scratch.txt
/opt/android-src/packages/providers/TelephonyProvider/src/com/android/providers/telephony/MmsSmsProvider.java
~/depot/android-conversation-backup/src/net/ugorji/android/conversationbackup/BaseCBActivity.java
/opt/android-src/frameworks/base/core/java/android/provider/CallLog.java
/opt/android-src/frameworks/base/core/java/android/provider/Telephony.java
/opt/android-src/packages/providers/TelephonyProvider/src/com/android/providers/telephony/MmsProvider.java
/opt/android-src/frameworks/base/core/java/com/google/android/mms/pdu/PduHeaders.java
~/depot/android-conversation-backup/src/net/ugorji/android/conversationbackup/ResultActivity.java
~/depot/android-conversation-backup/res/layout/result.xml
~/depot/android-conversation-backup/res/layout/main.xml
/opt/android-src/frameworks/base/telephony/java/android/telephony/PhoneNumberUtils.java

Notes:
- It might say email failed, even though email was successfully sent.

UPDATE CHECKLIST
================
- Update SAFETY_XXX variables in Helper
- in AndroidManifest.xml, increment versionCode (+1) and versionName (appropriately)
- update versionName to be same as value in Helper.VERSION

NEW FEATURES
============
- show name on messages
- allow filtering by name (ie use json to find where name matches a regex)

To get the name for SMS
- get recipient id, and use that to look into thread id to get person id, 
- and use that in contacts provider to get

take the phone number, and return the display name.

- get all the numbers (for call logs and sms)
- do a query to return a mapping of number to array containing canonical number, display name, etc
- put these into the in-memory entities
- so we need access to contacts database

fixes

- fixed potential out of memory error as we held onto each attachment in memory while uploading
- added contact name in the messages view for easy correlation
- added search to the UI (so you can filter by name, number, call type / message type, etc)
- changed how viewing works, so it works on Google Chrome and all browsers 
- allow selecting specific contacts through contacts picker
- improved viewing performance

view for sms, mms, incoming, outgoing, etc

ie flags column

search can specify many things
- we will tokenize on space and comma
- then we will take each token, and check in each row
- then we will find all matches, and have those ones be visible

make everything faster

so u can filter for: mms ibongile incoming missed etc




acb_messages.js
acb_call_logs.js
acb_summary.js
acb_script.js
acb_style.css
index.html
[ ... attachments ... ]

dir/zip name: acb_2011_01_20__23_10_12__UTC[.zip]

use script src to load everything.

