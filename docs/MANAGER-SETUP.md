# Clive Manager: setup steps for Jamie (laptop)

Do these in order. Each step is one small thing. Tell Claude "step N done" (or what went wrong) after each part. **Never paste a key file or its contents anywhere.**

## Part A: Firebase (about 10 minutes)

Project: **Church Drive Smart Home** (`church-drive-smart-home`).

**A1.** Open <https://console.firebase.google.com> and click **Church Drive Smart Home**.

**A2.** In the left menu click **Build**, then **Firestore Database**.

**A3.** Click **Create database**.

**A4.** Pick the location **`europe-west2` (London)**. It cannot be changed later. Click **Next**.

**A5.** Choose **Start in production mode**. Click **Create**. Wait until the empty database page shows.

**A6.** In the left menu click **Build**, then **Hosting**, then **Get started**. Click **Next** through the intro screens (no need to run any commands). Finish with **Continue to console**.

**A7.** Check the bottom of the left menu says **Spark** (free). Do not upgrade. If it shows a prompt to upgrade, ignore it.

Authentication is not used: do not turn on Google sign-in.

## Part B: A key so Home Assistant can write the live colours (about 10 minutes)

**B1.** Open <https://console.cloud.google.com> and check the project name at the top is **Church Drive Smart Home**. If not, click it and choose it.

**B2.** Open the menu (three lines), **IAM and Admin**, then **Service accounts**.

**B3.** Click **Create service account**.

**B4.** Name: `clive-ha-writer`. Click **Create and continue**.

**B5.** Under "Select a role" type **Cloud Datastore User** and pick it. Click **Continue**, then **Done**.

**B6.** Click the new `clive-ha-writer` account, then the **Keys** tab.

**B7.** Click **Add key**, then **Create new key**, choose **JSON**, click **Create**. A file downloads. Do not open or share it.

**B8.** Put that file on Home Assistant at `/config/church_drive/firebase-writer.json`. Easiest ways, pick one:
- **File editor or Studio Code Server add-on:** make the folder `church_drive` in `/config` if it is missing, then upload the file into it.
- **Samba share add-on:** open the `config` share, make the folder `church_drive`, copy the file in.

**B9.** Rename the file to exactly `firebase-writer.json` if it has a long name.

**B10.** Delete the downloaded copy from the laptop's Downloads folder (and empty the bin).

## Part C: Letting the website talk to Home Assistant (done 2026-11-11)

Newer Home Assistant keeps its web server settings in its own screen, not in `configuration.yaml` (a repair says "HTTP YAML configuration is ignored after migration" if the `http:` block is still there).

**C1.** In Home Assistant, go to **Settings**, **System**, **Network**, and scroll to **HTTP server**.

**C2.** Open **More options**. Under **CORS allowed origins**, tap **Add CORS allowed origins** and enter exactly `https://church-drive-smart-home.web.app`. Keep the existing `https://cast.home-assistant.io`.

**C3.** Tap **Save**. It restarts Home Assistant and asks an administrator to confirm.

**C4.** If `configuration.yaml` still has an `http:` block, delete it (it is ignored, and stops working in Home Assistant 2027.2.0).

If the site is later given a custom address, add that address to the same list.

## Part D: Tell Claude

Say: "Parts A, B and C done." Claude can then check what it can from Home Assistant (that the key file is in place and the setting is loaded) and start the integration work. If a step does not look like this description (the console changes), tell Claude what you see on screen.

## Part E: Letting GitHub publish the website (when you want it live)

The site is in `web/manager`. Nothing publishes it until GitHub has a key, so it is safe to leave this for later.

**E1.** In the Google Cloud console (project **Church Drive Smart Home**), go to **IAM and Admin**, then **Service accounts**, and create `clive-site-deployer` (display name `Clive Site Deployer`).

**E2.** Give it these roles: **Firebase Hosting Admin**, **Firebase Rules Admin** and **Service Usage Consumer**. Click **Done**.

**E3.** Open the account, **Keys** tab, **Add key**, **Create new key**, **JSON**. A file downloads.

**E4.** In GitHub, open the `church-drive-cards` repository, then **Settings**, **Secrets and variables**, **Actions**, **New repository secret**. Name it `FIREBASE_SERVICE_ACCOUNT` and paste the whole contents of the file as the value. (This is the one place a key goes. Never paste it in chat.)

**E5.** Delete the downloaded file from your device.

**E6.** In GitHub, **Actions**, **Clive Manager site**, **Run workflow**. It publishes the site and the Firestore rules. If it complains about a permission, tell Claude what it says.

The site is then at `https://church-drive-smart-home.web.app`, and every later change to `web/manager` publishes itself when it is merged.
