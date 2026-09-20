# TakeOFF test drivers and documents

**Everything in this folder that starts with `TEST_` is fictional test data.** The names, ID numbers, licence numbers,
plates, VINs, addresses and policy numbers are invented, and every document is stamped
"SPECIMEN - TEST DATA ONLY". There are no real identity or document details anywhere, and none of these files is
issued by, or imitates the branding of, a real authority or company. Do not replace them with real documents.

Each driver has three files the application accepts (PDF, JPG or PNG, up to 5 MB): a driver's licence (PNG image),
a vehicle registration (PDF) and an insurance certificate (PDF).

## Before you start

- The **National ID** and the **plate number** must be unique across drivers, so each test driver can only be created
  once in a given database. To repeat a test, use the other driver (or change the last digits of the ID and plate).
- The form checks the licence expiry is in the future and the driver is at least 18 (both are true for these values).
- The uploaded files are stored privately; an administrator opens them from the review page.
- Sign-up needs a phone number that can receive the one-time code. Use your own number, or, where the evaluator test
  number is enabled (development builds only), `+15550199` with the code `123456`.

## Test driver 01: Tafadzwa Test-Driver

Type these into the application steps (Application > My application). Every value is fictional.

| Step | Field | Value |
|---|---|---|
| 1. Personal details | Date of birth | `1990-05-14` |
| | Address | `12 Sample Road, Avondale` |
| | City or town | `Harare` |
| | Emergency contact name | `Test Contact One` |
| | Emergency contact phone | `+12025550143` (a reserved fictional US number) |
| 2. Identity and licence | National ID number | `99-000001 T 99` |
| | Driver's licence number | `TST-000001` |
| | Licence class | `4` |
| | Licence expiry date | `2031-12-31` |
| 3. Vehicle | Vehicle type | Pickup |
| | Registration (plate) number | `TST 0001` |
| | Make | Toyota |
| | Model | Hilux |
| 4. Documents | Driver's licence | `TEST_01_Tafadzwa-Test-Driver_Drivers-Licence.png` |
| | Vehicle registration | `TEST_01_Tafadzwa-Test-Driver_Vehicle-Registration.pdf` |
| | Insurance certificate | `TEST_01_Tafadzwa-Test-Driver_Insurance-Certificate.pdf` |

## Test driver 02: Rudo Sample-Driver

Type these into the application steps (Application > My application). Every value is fictional.

| Step | Field | Value |
|---|---|---|
| 1. Personal details | Date of birth | `1992-11-03` |
| | Address | `34 Test Avenue, Hillside` |
| | City or town | `Bulawayo` |
| | Emergency contact name | `Test Contact Two` |
| | Emergency contact phone | `+12025550144` (a reserved fictional US number) |
| 2. Identity and licence | National ID number | `99-000002 R 99` |
| | Driver's licence number | `TST-000002` |
| | Licence class | `2` |
| | Licence expiry date | `2032-06-30` |
| 3. Vehicle | Vehicle type | Van |
| | Registration (plate) number | `TST 0002` |
| | Make | Nissan |
| | Model | NV200 |
| 4. Documents | Driver's licence | `TEST_02_Rudo-Sample-Driver_Drivers-Licence.png` |
| | Vehicle registration | `TEST_02_Rudo-Sample-Driver_Vehicle-Registration.pdf` |
| | Insurance certificate | `TEST_02_Rudo-Sample-Driver_Insurance-Certificate.pdf` |

## Suggested run through the flow

1. Sign up (or sign in) as a driver and verify the phone with the one-time code.
2. Fill the four steps above for test driver 01, upload the three files, review, and submit.
3. Note the **Reference ID** on the completion screen (`TKO-YYYYMMDD-XXXXXX`) and that the status is *Pending review*.
4. Sign in as the administrator, open **Applications**, open the new entry, check every field and open each document.
5. Approve one application and reject another (a reason is required), then sign back in as the drivers and check the
   status and the notification bell.
