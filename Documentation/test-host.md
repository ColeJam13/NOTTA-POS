# Running NOTTA-POS on a test host

How to put the app on one Windows machine at home and use it from phones and tablets on the same Wi-Fi. Written for a machine with nothing installed, one step at a time.

On that machine the app is **one file**, `notta-pos.jar`. It holds the backend and the screens together, so there is one program to start and one address to open: `http://<the machine's address>:8080`. It needs two things installed beside it: Java (to run the file) and PostgreSQL (the database).

**Status of this guide.** The package itself was built and run on the development machine (Linux), from the same address a phone would use. The Windows steps below were written from the tools' documentation and could not be run on the real machine by the person who wrote them. The two scripts were checked for syntax with Windows PowerShell 5.1 and nothing more. The checklist at the end is what to confirm on the real machine; please report anything that did not go as written.

## Rules for the test host

Read these first. They are short and they matter.

1. **Home network only.** Do not forward a port to this machine on the router, and do not put it on a public or guest Wi-Fi. Windows 10 no longer gets security updates, so the machine must not be reachable from the internet. The firewall rule in step 7 is for private networks only, on purpose.
2. **Change the first manager's PIN before any other device connects.** A new database has one manager whose PIN is 1234, and that PIN is written in this public repository. The app makes that manager choose a new PIN at the first sign-in (step 5). Do it on the machine itself, before step 7 lets other devices in.
3. **Back up before every update** (the "Updating" section). An update can change the database's tables, and only a backup undoes that.
4. **Sign-in and wrong-PIN protection must be in the build** (pull requests #281 to #283). Any package made from `main` after 21 November 2026 has them.
5. **No proxy in front of it, for now.** The app counts wrong PINs per device address. If a program that passes requests on (a reverse proxy) is ever put in front of the app, every phone would look like one device unless the proxy hands over the real address and the app is told to read it (`server.forward-headers-strategy`). This guide uses no proxy.

## What you need

| | Where |
|---|---|
| The package: `notta-pos.zip` | Made on the development machine, below |
| Java 21 | Step 1 |
| PostgreSQL 16 or 17 | Step 2 |
| About 300 MB of disk, and an account that can install programs | The Windows machine |

## Make the package (on the development machine)

From the project folder, on an up-to-date `main`:

```bash
./package.sh
```

It builds the screens, puts them inside the backend, and leaves two things:

- `release/notta-pos/`: the folder to copy
- `release/notta-pos.zip`: the same folder as one file

In the folder:

| File | What it is |
|---|---|
| `notta-pos.jar` | The app |
| `start-notta-pos.cmd` | Double-click to start the app |
| `backup-notta-pos.cmd` | Double-click to back up the database |
| `start-notta-pos.ps1`, `backup-notta-pos.ps1` | The scripts those two run |
| `application-local.properties.example` | The settings file to copy and fill in (step 4) |
| `peach-blossom-seed-data.sql` | The demo restaurant's menu and tables (step 6) |
| `test-host.md` | This guide |
| `VERSION.txt` | When the package was made and from which commit |

Get `notta-pos.zip` to the Windows machine any way you like (a USB stick, a shared folder). If the development machine is WSL on a Windows PC, this copies it to that PC's desktop:

```bash
cp release/notta-pos.zip /mnt/c/Users/YOUR-WINDOWS-NAME/Desktop/
```

## Set up the Windows machine (once)

### Step 1. Install Java 21

1. Go to https://adoptium.net and download **Temurin 21 (LTS)** for Windows x64, the `.msi` installer. The JRE is enough; the JDK also works.
2. Run it. On the "Custom Setup" page, make sure **Add to PATH** is set to install (it is by default).
3. Check it. Open a **new** PowerShell window (Start, type `powershell`) and run:

```powershell
java -version
```

The first line should say `openjdk version "21...`. If it says "not recognized", the window was open before the install finished: close it and open a new one.

### Step 2. Install PostgreSQL

1. Go to https://www.postgresql.org/download/windows/ and download the installer for **version 16 or 17**.
2. Run it and accept the defaults, with two things to note:
   - It asks for a password for the database superuser, named `postgres`. Choose one and write it down. This is **not** the password the app uses; it is the one you need for step 3 and for repairs.
   - Leave the port at **5432**.
   - At the end it offers to launch "Stack Builder". Untick that; it is not needed.
3. PostgreSQL is now a Windows service: it starts with the machine and runs in the background. To see it, press Win+R, type `services.msc`, and look for a name that starts with `postgresql`.

### Step 3. Create the database and the app's user

1. Open **SQL Shell (psql)** from the Start menu (it was installed with PostgreSQL).
2. It asks for Server, Database, Port, and Username. Press Enter at each to accept what is in the brackets. At "Password for user postgres", type the superuser password from step 2.
3. At the `postgres=#` prompt, run these two lines, with a password of your own in place of `choose-a-password`. This is the password the app will use. Letters and digits only keeps step 4 simple.

```sql
CREATE USER nottauser WITH PASSWORD 'choose-a-password';
CREATE DATABASE notta_pos OWNER nottauser;
```

Each should answer `CREATE ROLE` and `CREATE DATABASE`. Type `\q` to leave.

The database is empty at this point. The app creates its tables the first time it starts (step 5).

### Step 4. Unpack the app and give it the database password

1. Unzip `notta-pos.zip` somewhere simple, for example so that the files are in `C:\notta-pos`.
2. In that folder, make a copy of `application-local.properties.example` and name the copy `application-local.properties`. (If Windows hides file endings, the copy should be the one whose Type is "PROPERTIES File".)
3. Open the copy in Notepad. Replace `put-your-database-password-here` with the password you chose in step 3, and save.

```properties
spring.datasource.password=choose-a-password
```

In this kind of file a backslash has a special meaning. If the password contains one, type it twice (`\\`).

### Step 5. Start it, and change the first PIN

1. Double-click **`start-notta-pos.cmd`**. A window opens, checks that Java, the settings file, and the database are in place, and starts the app. If something is missing it says what, in plain words.
2. Wait for a line that ends `Started NotaPosApplication in ... seconds`. On the first start, the lines before it show the tables being created (`Migrating schema "public" to version ...`) and a warning that a first manager was created with PIN 1234.
3. On this machine, open http://localhost:8080 in a browser. The PIN pad appears.
4. Enter **1234**. The app asks for the current PIN (1234 again), then a new one, then the new one again. Choose it now. This is rule 2.

The window is the app. **Closing it, or pressing Ctrl+C in it, stops the app.** Leave it open while the app is in use.

Windows may show "Windows Defender Firewall has blocked some features of this app" for Java at this point. Tick **Private networks** only, leave Public unticked, and allow. Step 7 adds the exact rule either way.

### Step 6. Load the demo menu and tables (once)

The app is running but has no menu and no tables. With the app started at least once (the tables must exist), open PowerShell **in the app's folder** (in File Explorer, open the folder, click the address bar, type `powershell`, press Enter) and run the following. Change `16` to your PostgreSQL version.

```powershell
$env:PGCLIENTENCODING = 'UTF8'
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U nottauser -d notta_pos -f peach-blossom-seed-data.sql
```

It asks for the password from step 3 and prints a line for each group of rows it adds (`INSERT 0 2`, and so on). Reload the browser: the Floor Map now has tables and the order screen has a menu.

Do this once. Run again, it stops with "duplicate key" errors and adds nothing.

### Step 7. Let other devices on the home network reach it

1. Check that Windows treats your home network as **Private**. Settings, Network & Internet, click the connection (Wi-Fi or Ethernet), and under "Network profile" choose **Private**. On a network marked Public the rule below does nothing, which is the point.
2. Open PowerShell **as Administrator** (Start, type `powershell`, right-click, "Run as administrator") and run:

```powershell
New-NetFirewallRule -DisplayName "NOTTA-POS" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
```

This lets devices on a private network connect to port 8080 on this machine, and nothing else. To take it away later:

```powershell
Remove-NetFirewallRule -DisplayName "NOTTA-POS"
```

### Step 8. Open it from a phone

1. The start window prints the address to use, on a line beginning "From a phone or tablet". It looks like `http://192.168.1.50:8080`. (To find it by hand, run `ipconfig` and look for "IPv4 Address".)
2. On a phone connected to the **same Wi-Fi**, open that address in the browser. The PIN pad appears. Sign in with the PIN you chose in step 5.
3. Add your staff on the STAFF screen, each with a role and a PIN.

The address can change when the machine restarts, because the router hands them out. To keep it the same, look in the router's settings for "DHCP reservation" or "static lease" and reserve the address for this machine.

The screens are not laid out for a phone yet (that is step P3 of the plan). A tablet in landscape is the closest to what they were designed for.

## Every day

- **Start:** double-click `start-notta-pos.cmd`. Wait for `Started NotaPosApplication`.
- **Stop:** press Ctrl+C in that window, or close it. People who were signed in stay signed in when it starts again.
- **Keep the machine awake** while the app is in use. If Windows goes to sleep, the phones lose the app until it wakes. Settings, System, Power & sleep.
- **The log** of the current and earlier runs is in `logs\notta-pos.log`.
- **Another port.** If something else on the machine already uses 8080 (some media servers do), the start script says so. Start with another one, and use that number in the firewall rule and in the address:

```powershell
.\start-notta-pos.cmd -Port 8090
```

## Updating to a newer package

1. **Back up** (next section). Every time.
2. Stop the app.
3. Unzip the new package and copy its files over the old ones in `C:\notta-pos`. Your `application-local.properties`, `backups`, and `logs` are not in the package, so they are left alone.
4. Start the app. If the new version changed the database's tables, it applies the changes during this first start (`Migrating schema ...` in the window), before anything else.

Do not go back to an older `notta-pos.jar` after a newer one has run. The older app checks the tables against what it expects and refuses to start when they are newer. The way back is the backup from step 1 together with the older package.

## Backing up, and putting a backup back

**To back up:** double-click **`backup-notta-pos.cmd`**. It writes the whole database to one file, `backups\notta_pos-<date>-<time>.dump`, and says how big it is. The app can stay running. Copy a backup to another device now and then: a backup on the same disk does not survive that disk.

**To put a backup back.** This replaces everything in the database with what was in the backup.

1. Stop the app.
2. In PowerShell, in the app's folder (change `16` to your PostgreSQL version, and the file name to the backup you want):

```powershell
& "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe" -U nottauser -d notta_pos --clean --if-exists backups\notta_pos-20261121-183000.dump
```

3. Type the database password when asked, wait for the prompt to return, and start the app.

If you are going back to an older version of the app as well, put the older `notta-pos.jar` in place before step 3.

## If something goes wrong

| What you see | Likely cause, and what to do |
|---|---|
| "Java is not installed" | Step 1, then open a **new** window. |
| "PostgreSQL is not answering on port 5432" | The service is stopped. Win+R, `services.msc`, find `postgresql...`, Start. |
| "application-local.properties is missing" or "still holds the placeholder text" | Step 4. |
| The app stops soon after starting, with `password authentication failed for user "nottauser"` in the window | The password in `application-local.properties` is not the one from step 3. |
| It stops with `database "notta_pos" does not exist` | Step 3 was skipped or mistyped. |
| It stops with `Schema-validation` or `Migration checksum mismatch` | An older jar was started against a newer database, or the reverse. See "Updating". |
| "Something is already using port 8080" | The app is already running in another window, or another program has the port. Use `-Port`. |
| Works on the machine, phone says "can't reach this site" | The firewall rule (step 7), the network marked Public instead of Private, the phone on mobile data or a guest Wi-Fi, or a wrong address. |
| The phone shows the PIN pad but every PIN is refused with a wait | Five wrong PINs pause that device, longer each time. Wait it out. Restarting the app also clears the counts. |
| The PIN pad appears with no styling, or a blank page | The package was made without the screens in it. `./package.sh` checks for this; make the package again. |
| Double-clicking the `.cmd` opens and closes at once | Open PowerShell in the folder and run `.\start-notta-pos.cmd` to read the message. |

## Trying the package on the development machine

On Linux or WSL the same jar runs without the Windows scripts. With PostgreSQL running and the backend from `./start.sh` stopped (both use port 8080):

```bash
cd release/notta-pos
cp ../../backend/application-local.properties .
java -jar notta-pos.jar
```

Open http://localhost:8080. It uses the development database.

## Checklist for the real machine

These are the things nobody has yet seen work on Windows. Tick them off the first time through, and say which did not go as written.

- [ ] Step 1: `java -version` answers 21 in a new window.
- [ ] Step 2: the installer's defaults matched what this guide says, and the service is listed in `services.msc`.
- [ ] Step 3: both SQL lines answered `CREATE ROLE` and `CREATE DATABASE`.
- [ ] Step 5: double-clicking `start-notta-pos.cmd` opened a window that reached `Started NotaPosApplication`.
- [ ] Step 5: the window listed an address that starts like your home network's addresses.
- [ ] Step 5: signing in with 1234 led to choosing a new PIN, and 1234 no longer works afterwards.
- [ ] Step 6: the seed file loaded with no errors, and item names with unusual characters look right on the order screen.
- [ ] Step 7: the firewall command was accepted, and the network is marked Private.
- [ ] Step 8: a phone on the Wi-Fi opened the address, signed in, rang in an item, sent it, and saw it on Active Orders.
- [ ] A phone on **mobile data** (Wi-Fi off) cannot open the address.
- [ ] `backup-notta-pos.cmd` wrote a file of more than a few KB into `backups`.
- [ ] Closing the start window stopped the app (the phone can no longer load it), and starting again left the phone signed in.
- [ ] Deliberate mistakes are caught in plain words: rename `application-local.properties` and start (it should say the file is missing), then put it back.
