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

## Part C: Letting the website talk to Home Assistant (about 5 minutes)

**C1.** In Home Assistant, open the **File editor** (or Studio Code Server) and open `configuration.yaml`.

**C2.** Look for a line starting `http:`.
- If there is **no** `http:` section, add this at the bottom:

  ```yaml
  http:
    cors_allowed_origins:
      - https://church-drive-smart-home.web.app
  ```

- If there **is** an `http:` section, add only the `cors_allowed_origins:` part (with the one address under it) inside it, keeping the existing lines such as `use_x_forwarded_for`.

**C3.** Save the file.

**C4.** Go to **Developer tools**, **YAML**, click **Check configuration**. It must say the configuration is valid. If it does not, tell Claude what it says and do not restart.

**C5.** Restart Home Assistant (**Settings**, **System**, power icon, **Restart Home Assistant**).

If you later give the site a custom address, it gets another line in the same list.

## Part D: Tell Claude

Say: "Parts A, B and C done." Claude can then check what it can from Home Assistant (that the key file is in place and the setting is loaded) and start the integration work. If a step does not look like this description (the console changes), tell Claude what you see on screen.
