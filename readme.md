# Android OpenCV AruCo Tracking

This project is largely based on [jsmith613's Aruco project](https://github.com/jsmith613/Aruco-Marker-Tracking-Android) as well 
as the various libraries he included. I made some modifications that make it easier to calibrate the camera as well as store 
camera parameters. In addition, all image processing is now done on a separate thread than the main OpenCV image thread to reduce 
latency. Many values, including resolution scale and threshold settings are hardcoded so there is some work to do to clean up the 
code. 

I decided to put this up after realising that there wasn't a great example of using OpenCV to track Aruco markers on Android, and 
because jsmith613's implementation wasn't compatible with the latest OpenCV version and had some major bugs which reduced 
performance. The package name is `engr100_vision` because I originally wrote this code to add vision target tracking to an 
autonomous quadcopter for my ENGR100 class at UMich.
