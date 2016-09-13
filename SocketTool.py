import socket


class TemperatureSocket:
    def __init__(self, sock=None):
        if sock is None:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        else:
            self.sock = sock

    def connect(self, host, port):
        self.sock.settimeout(5.0)
        self.sock.connect((host, port))

    def send_message(self, mess):
        if (isinstance(mess, basestring)):
            string_mess = mess
        else:
            string_mess = "{:.4f}".format(mess)

        string_mess = string_mess + "\n"
        total_bytes_sent = 0
        self.sock.sendall(string_mess)

    #	while total_bytes_sent < string_temp.len():
    #		bytes_sent = self.sock.send(string_temp[bytes_sent:])
    #		if bytes_sent == 0:
    #			raise RuntimeError("Socket connection broken.")
    #		total_bytes_sent = total_bytes_sent + bytes_sent

    def end_connection(self):
        self.sock.shutdown(1)
        self.sock.close()

    def recieve_message(self):
        return self.sock.recv(64);
