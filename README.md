# Milk Khata 🥛

An intelligent Android application designed to automate the tracking and management of daily milk delivery slips. Built with Java and Android Studio, Milk Khata leverages Google ML Kit for seamless OCR extraction, Room Database for local storage, and MPAndroidChart for visual data analysis.

## ✨ Key Features

**📸 Smart OCR & Auto-Fill (Home Tab)**
* Built-in Document Scanner with custom image sharpening and contrast optimization.
* Regex-powered data extraction parsing Date, Quantity, Rate, Amount, and Incentive.
* **Smart Math Fallback:** Automatically calculates missing values if receipt text is misaligned.
* Strict UI validation ensuring accurate `dd/MM/yyyy` inputs and mathematical accuracy (`Qty * Rate == Amount`).

**📜 History & Tracking (History Tab)**
* View all saved slips sorted chronologically.
* Smart duplicate detection instantly highlights multiple entries on the same date.
* Precise Start and End date filtering.
* Tap to view receipt details, original cropped image, and save images to the public gallery.

**📊 Analytics Dashboard (Analysis Tab)**
* Dynamic visual charts powered by MPAndroidChart.
* Track **Amount per Day** (Line Chart), **Average Rate per Day** (Bar Chart), **Quantity per Day** (Bar Chart), and **Entry Frequency** (Bar Chart).
* Apply custom date ranges to isolate specific billing periods.

**📁 Export & Share**
* One-click database export to a formatted `.csv` file.
* Share directly to WhatsApp, Google Drive, or Email using native Android sharing.

## 🛠️ Tech Stack

* **Language:** Java
* **UI/UX:** XML, Material Design, ColorStateLists, Dark Mode Support
* **Database:** Room Persistence Library (SQLite)
* **Machine Learning:** Google ML Kit (Document Scanner API, Text Recognition API)
* **Data Visualization:** MPAndroidChart (v3.1.0)
* **Concurrency:** `java.util.concurrent.Executors` for background database operations

## 🚀 Installation

### Option 1: Install the APK directly
1. Go to the [Releases](../../releases) section of this repository.
2. Download the latest `Milk_Khata_v1.apk`.
3. Transfer the file to your Android device and install it (ensure "Install from Unknown Sources" is enabled in your device settings).

### Option 2: Build from Source
1. Clone the repository:
   ```bash
   git clone [https://github.com/RohitSingh-04/milk-khata.git](https://github.com/RohitSingh-04/milk-khata.git)
   ```
2. Open the project in Android Studio.
3. Allow Gradle to sync and download the required dependencies (Room, ML Kit, MPAndroidChart).
4. Click Run (Shift + F10) to deploy to an emulator or connected physical device

##👨‍💻 Developer
Rohit Singh
- GitHub: @RohitSingh-04
- LinkedIn: linkedin.com/in/rohiyaa
