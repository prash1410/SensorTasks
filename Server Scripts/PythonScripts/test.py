import os
import time
import sys
import MySQLdb as mySQL
import subprocess

filename = sys.argv[1]
deviceID = sys.argv[2]
latitude = sys.argv[3]
longitude = sys.argv[4]

filename = 'C:\\wamp64\\www\\PHPScripts\\acoustic_data\\' + filename
tempPath = filename.split(".")
subprocess.call(['C:\\ffmpeg\\bin\\ffmpeg.exe', '-i', filename, tempPath[0]+".wav"])

try:
    os.remove(filename)
except OSError:
    pass

filename = tempPath[0]+".wav"

fileSize = str(round(os.path.getsize(filename) / 1024.0, 3)) + " kB"
timestamp = (time.ctime(os.path.getctime(filename))).split(" ")

database = mySQL.connect(host="localhost", user="root", passwd="", db="acousticdatabase")
cursor = database.cursor()

tempProcessedFilename = sys.argv[1]
processedFilename = tempProcessedFilename.split(".")
processedFilename[0] = processedFilename[0] + ".wav"

cursor.execute("CREATE TABLE IF NOT EXISTS device_"+deviceID+" (filename VARCHAR(50), filesize VARCHAR(50), timestamp VARCHAR(50), latitude VARCHAR(50), longitude VARCHAR(50), read_flag INT)")
cursor.execute("INSERT INTO device_"+deviceID+" VALUES ('"+processedFilename[0]+"','"+fileSize+"','"+timestamp[3]+"','"+latitude+"','"+longitude+"',0)")

database.close()
