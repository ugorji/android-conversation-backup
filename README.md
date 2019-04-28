# android-conversation-backup

*Backup and archive text, pictures and other multimedia messages and call logs.*

This tool can backup your SMS messages, MMS messages with included
attachments, and call records, into a zip archive. You can specify a set
of numbers to limit the conversations backed up. Optionally, you can
request that those archived messages and/or call logs from your device
are deleted. 

Once backup is done, you can `share` the zip archive, allowing
you store it using any apps on your device which can share (e.g. email, gmail,
Google Drive, DropBox, Box, etc).

Finally, you can review the prior backups done, and delete them or `share` them
again (re-send them somewhere).

Primer / Documentation: http://ugorji.net/project/android-conversation-backup  
Blog Post: http://ugorji.net/blog/android-conversation-backup  
Download on Google Play: https://play.google.com/store/apps/details?id=net.ugorji.android.conversationbackup

## Development Notes

Google doesn't provide public API's for accessing SMS and MMS.

I had to reverse-engineer the actual android src to know what to do.
However, this has been tested and works on Androind 2.3 up to Android 9.0.

