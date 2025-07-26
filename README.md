# Follow Me - Real-Time Location Sharing App

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Java](https://img.shields.io/badge/Language-Java-orange.svg)](https://www.java.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📱 Overview

**Follow Me** is an Android application that enables real-time location sharing during trips, allowing users to track and follow journeys as they happen. Whether you're traveling by car, bike, or any other mode of transportation with location tracking capabilities, this app keeps everyone connected to your journey.

## ✨ Features

### For Trip Leaders
- **Real-time Location Sharing**: Share your live location updates during any trip
- **Unique Trip IDs**: Generate and share unique trip identifiers for easy access
- **Trip Management**: Start, pause, and end trips with full control
- **Journey Analytics**: View total distance traveled, elapsed time, and trip statistics

### For Trip Followers  
- **Live Trip Tracking**: Follow any trip using a shared trip ID
- **Interactive Map View**: Real-time map updates with zoom, pan, and center controls
- **Trip Progress Monitoring**: Track distance, duration, and trip start time
- **Multiple Trip Support**: Follow multiple trips simultaneously

### Universal Features
- **Interactive Maps**: Powered by Google Maps SDK with smooth navigation
- **Comprehensive Trip Details**: Distance traveled, elapsed time, and start time display
- **Real-time Updates**: Live location synchronization across all connected devices
- **User-friendly Interface**: Material Design UI for intuitive navigation

## 🛠️ Technical Stack

- **Language**: Java
- **Architecture**: MVVM (Model-View-ViewModel)
- **Maps**: Google Maps SDK
- **Location Services**: Android LocationManager
- **UI Framework**: Material Design Components
- **Development Environment**: Android Studio

## 📋 Requirements

- **Android Version**: 6.0 (API level 23) or higher
- **Permissions**: Location access, Internet connectivity
- **Hardware**: GPS-enabled Android device
- **Network**: Active internet connection for real-time updates

## 🚀 Getting Started

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/follow-me-app.git
   cd follow-me-app
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned repository folder

3. **Configure Google Maps API**
   - Obtain a Google Maps API key from [Google Cloud Console](https://console.cloud.google.com)
   - Add your API key to `app/src/main/res/values/google_maps_api.xml`:
     ```xml
     <string name="google_maps_key" templateMergeStrategy="preserve" translatable="false">YOUR_API_KEY_HERE</string>
     ```

4. **Build and Run**
   - Connect your Android device or start an emulator
   - Click "Run" in Android Studio or use `./gradlew installDebug`

## 📖 How to Use

### As a Trip Leader

1. **Start a Trip**
   - Open the app and tap "Start New Trip"
   - Grant location permissions when prompted
   - Your unique Trip ID will be generated and displayed

2. **Share Trip ID**
   - Share the generated Trip ID with followers via text, email, or social media
   - Trip followers can use this ID to track your journey

3. **Monitor Your Trip**
   - View your real-time location on the map
   - Track distance traveled and elapsed time
   - End the trip when your journey is complete

### As a Trip Follower

1. **Join a Trip**
   - Open the app and tap "Follow Trip"
   - Enter the Trip ID shared by the Trip Leader
   - Tap "Start Following"

2. **Track the Journey**
   - View the Trip Leader's real-time location on the map
   - Monitor trip progress including distance and duration
   - Use map controls to zoom, pan, or center on current location

## 🏗️ Project Structure

```
app/
├── src/main/java/com/followme/
│   ├── activities/          # Activity classes
│   ├── fragments/           # Fragment classes  
│   ├── viewmodels/          # ViewModel classes
│   ├── models/              # Data models
│   ├── services/            # Background services
│   ├── utils/               # Utility classes
│   └── adapters/            # RecyclerView adapters
├── src/main/res/
│   ├── layout/              # XML layout files
│   ├── values/              # Colors, strings, styles
│   └── drawable/            # Images and icons
└── build.gradle             # App-level dependencies
```

## 🤝 Contributing

I welcome contributions to improve Follow Me! Here's how you can help:

1. **Fork the repository**
2. **Create a feature branch**: `git checkout -b feature/amazing-feature`
3. **Make your changes** and test thoroughly
4. **Commit your changes**: `git commit -m 'Add amazing feature'`
5. **Push to the branch**: `git push origin feature/amazing-feature`
6. **Open a Pull Request**

### Contribution Guidelines

- Follow Java coding conventions and best practices
- Ensure all new features include appropriate documentation
- Test your changes on multiple Android versions and screen sizes
- Update the README if you add new features or change functionality


## 🐛 Bug Reports & Feature Requests

Found a bug or have a feature idea? We'd love to hear from you!

- **Bug Reports**: [Create an issue](https://github.com/yourusername/follow-me-app/issues) with detailed steps to reproduce
- **Feature Requests**: [Open a discussion](https://github.com/yourusername/follow-me-app/discussions) to share your ideas

## 📞 Contact

**Adarsh Purushothama Reddy**
- Email: reddyadarsh164@gmail.com
- LinkedIn: [Your LinkedIn Profile](https://linkedin.com/in/yourprofile)
- GitHub: [@yourusername](https://github.com/yourusername)

## 🙏 Acknowledgments

- Google Maps Platform for mapping services
- Material Design team for UI components
- Android development community for inspiration and support


![image](https://github.com/user-attachments/assets/e89a7a82-4d10-4280-b9b0-37ccfa78bd45)


![image](https://github.com/user-attachments/assets/8da936ea-a0a2-44dd-b5f6-4bb607536e11)

![image](https://github.com/user-attachments/assets/5d88daf5-4f51-4cf8-afeb-e71e82cc17f5)


