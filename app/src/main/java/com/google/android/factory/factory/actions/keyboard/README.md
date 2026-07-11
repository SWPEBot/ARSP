## 键盘测试指南

- ARSP 修改layout file   {arsp}/device/google/desktop/common/keyboard/keyboard-layout.json
- in the device     /product/etc/keyboard-layout.json   
- 无法有效通过region切换layout 
- 修改方法: adb root && adb remount && adb reboot
