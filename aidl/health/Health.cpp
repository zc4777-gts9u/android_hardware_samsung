/*
 * Copyright (C) 2021 The Android Open Source Project
 * Copyright (C) 2022 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <unistd.h>
#include <android/binder_interface_utils.h>
#include <health-impl/Health.h>
#include <health/utils.h>

#ifndef CHARGER_FORCE_NO_UI
#define CHARGER_FORCE_NO_UI 0
#endif

#if !CHARGER_FORCE_NO_UI
#include <health-impl/ChargerUtils.h>
#endif

using aidl::android::hardware::health::HalHealthLoop;
using aidl::android::hardware::health::Health;

#if !CHARGER_FORCE_NO_UI
using aidl::android::hardware::health::charger::ChargerCallback;
using aidl::android::hardware::health::charger::ChargerModeMain;
#endif

static constexpr const char* gInstanceName = "default";
static constexpr std::string_view gChargerArg{"--charger"};

#if !CHARGER_FORCE_NO_UI
namespace aidl::android::hardware::health {
class ChargerCallbackImpl : public ChargerCallback {
  public:
    using ChargerCallback::ChargerCallback;
    bool ChargerEnableSuspend() override { return true; }
};
}  // namespace aidl::android::hardware::health
#endif

namespace {

void useBatteryPathIfReadable(android::String8& path, const char* candidate) {
    if (access(candidate, R_OK) == 0) {
        path = candidate;
    }
}

class SamsungHealth : public Health {
  public:
    SamsungHealth(std::string_view instanceName, std::unique_ptr<healthd_config> config,
                  bool isGts9uWifi)
        : Health(instanceName, std::move(config)), isGts9uWifi_(isGts9uWifi) {}

  protected:
    void UpdateHealthInfo(aidl::android::hardware::health::HealthInfo* info) override {
        if (isGts9uWifi_ && info->batteryPresent) {
            // SM-X910 CYG1 power profile: rated minimum 10880 mAh, typical 11200 mAh.
            // The legacy driver exposes a fixed capacity, so use the rated value
            // as its nominal full-charge fallback.
            constexpr int32_t kRatedCapacityUah = 10880000;
            info->batteryFullChargeDesignCapacityUah = kRatedCapacityUah;
            info->batteryFullChargeUah = kRatedCapacityUah;
        }
    }

  private:
    const bool isGts9uWifi_;
};

}  // namespace

int main(int argc, char** argv) {
#ifdef __ANDROID_RECOVERY__
    android::base::InitLogging(argv, android::base::KernelLogger);
#endif

    // make a default health service
    auto config = std::make_unique<healthd_config>();
    ::android::hardware::health::InitHealthdConfig(config.get());
    const bool isGts9uWifi =
            android::base::GetProperty("ro.product.vendor.device", "") == "gts9uwifi";
    if (isGts9uWifi) {
        // The legacy current_now/current_avg properties return mA on this device.
        useBatteryPathIfReadable(config->batteryCurrentNowPath,
                                 "/sys/class/power_supply/battery/batt_current_ua_now");
        useBatteryPathIfReadable(config->batteryCurrentAvgPath,
                                 "/sys/class/power_supply/battery/batt_current_ua_avg");
        useBatteryPathIfReadable(config->batteryCycleCountPath,
                                 "/sys/class/power_supply/battery/fg_cycle");
        useBatteryPathIfReadable(config->batteryStateOfHealthPath,
                                 "/sys/class/power_supply/battery/fg_asoc");
    }
    auto binder =
            ndk::SharedRefBase::make<SamsungHealth>(gInstanceName, std::move(config), isGts9uWifi);

    if (argc >= 2 && argv[1] == gChargerArg) {
#if !CHARGER_FORCE_NO_UI
        // If charger shouldn't have UI for your device, simply drop the line below
        // for your service implementation. This corresponds to
        // ro.charger.no_ui=true
        return ChargerModeMain(
                binder,
                std::make_shared<aidl::android::hardware::health::ChargerCallbackImpl>(binder));
#endif

        LOG(INFO) << "Starting charger mode without UI.";
    } else {
        LOG(INFO) << "Starting health HAL.";
    }

    auto hal_health_loop = std::make_shared<HalHealthLoop>(binder, binder);
    return hal_health_loop->StartLoop();
}
