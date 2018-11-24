## About

This repository is about a temperature regulator/logger system for beehives.
It contains the sketch for the ESP8266s and the source code of a java application which acts as the hub for the ESPs.
Due to the dangers of over/underheating of hives, the hub-sensor communication has redudancy layers for safety.

## How it works

You can configure whether you would like heating enabled and the temperature which the sensor should heat up to.
You are also be to post timed configurations: at the specified time, the active configuration changes to that.
The ESPs expose a REST API, they communicate with the hub through that.
The heating is done using a resistance.

## The interface

The hub application hosts a simple, but powerful webpage, here is an image of that:
![image](https://i.imgur.com/umUAxVa.png)
