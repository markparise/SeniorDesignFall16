import os
import time

os.system('modprobe w1-gpio')
os.system('modprobe w1-therm')

temp_sensor = '/sys/bus/w1/devices/28-000007298df8//w1_slave'


def get_raw_temp_data():
    try:
        f = open(temp_sensor, 'r')
        lines = f.readlines()
        f.close()
        return lines

    except IOError:
        return "ErrUn"  # sensor unplugged


# returns temp in c deg or ErrUn if sensor is unplugged
def read_temp():
    temp_raw = get_raw_temp_data()
    if temp_raw == "ErrUn":
        return "ErrUn"
    while temp_raw[0].find('YES') == -1:
        return "ErrUn"

    temp_output = temp_raw[1].find('t=')
    if temp_output != -1:
        temp_string = temp_raw[1][temp_output + 2:]
        temp_c = float(temp_string) / 1000.0
        return temp_c


# test by running TempProbe.py as main
if __name__ == '__main__':
    while True:
        print(read_temp())
        time.sleep(1)

