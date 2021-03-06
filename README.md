# ImageCropper

It crops image using multiple mode (Crop mode, Crop-Strecth mode, Crop-Shrink mode)
Also, you can use pen or eraser to decorate Image. <br>

## Gradle build
build.gradle
<pre>
buildscript {
    repositories {
        jcenter()
    }
}

dependencies {
    ...
    // Daniel (2016-06-23 14:37:35): Added Image Cropper
    implementation 'com.danielworld:image-crop-library:2.0.5'
    
    // For logging cropper
    implementation 'com.danielworld:logger:1.0.4'
    
    // For Cropper view
    implementation 'androidx.appcompat:appcompat:1.1.0'
}
</pre>

## CROP_STRETCH mode
![]()
<img src="https://cloud.githubusercontent.com/assets/9348174/17641593/b858f648-6161-11e6-9783-6cc801b51b25.png" width="240">
<img src="https://cloud.githubusercontent.com/assets/9348174/17641595/ba3a1280-6161-11e6-9ee5-2b99fe0af2e9.png" width="240">

## CROP mode
![]()
<img src="https://cloud.githubusercontent.com/assets/9348174/17641596/bb9bf3be-6161-11e6-9f7f-b301a98abfa0.png" width="240">
<img src="https://cloud.githubusercontent.com/assets/9348174/17641597/bd776ef2-6161-11e6-95e7-a23f35dd500d.png" width="240">

## CROP_SHRINK mode
![]()
<img src="https://cloud.githubusercontent.com/assets/9348174/17641598/beff0bcc-6161-11e6-9581-5eee067d0bf6.png" width="240">
<img src="https://cloud.githubusercontent.com/assets/9348174/17641601/c0b22832-6161-11e6-8c69-fa5f5518e2e3.png" width="240">

## PEN mode (Eraser or Pen)
![]()
<img src="https://cloud.githubusercontent.com/assets/9348174/17641602/c2cbf710-6161-11e6-9713-939fe5e3d0e5.png" width="240">

## NONE mode
![]()
<img src="https://cloud.githubusercontent.com/assets/9348174/17641603/c45429f4-6161-11e6-9b8a-21ec71a8028b.png" width="240">

## License
ImageCropper is licensed under the Apache License, Version 2.0.
See [LICENSE](LICENSE.txt) for full license text.

```
Copyright (c) 2016-2019 DanielWorld.
@Author Namgyu Park

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
