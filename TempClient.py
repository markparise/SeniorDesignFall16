import os
import time
import SocketTool
import TempProbe

socket = SocketTool.TemperatureSocket()
socket.connect('192.168.1.14', 51163)
socket.send_message('Pi')
time.sleep(1)

button = bool(0)
connected = bool(1)
while (connected):
    mess = TempProbe.read_temp()
    print("Sending message: ")
    print(mess)
    socket.send_message(mess)
    server_message = socket.recieve_message()
    if (server_message == 'true'):
        button = bool(1)
        print("button set to true")

        # function call to exec binaryLEDv2 script
        # this will get back a temp and light the LEDs in binary
        os.system("binaryLED.py, 1")


    elif (server_message == 'false'):
        button = bool(0)
        print("button set to false")
    else:
        connected = bool(0)

    time.sleep(1)

socket.send_message('Bye')
socket.end_connection()
