#version 100
// ZALITH BALANCED SHADER (Kanpur Dev Room Edition)
// Ultra-Fast Post-Processing: Vibrance + Fake Atmospheric Fog
precision mediump float;

varying vec2 v_texcoord;
uniform sampler2D u_texture;

void main() {
    // 1. Base Game Graphics (Original Frame fetch karna)
    vec4 baseColor = texture2D(u_texture, v_texcoord);

    // 2. Zalith Color Filter (Saturation aur Contrast badhana)
    // Isse ghaas zyada hari aur aasmaan zyada neela lagega
    float luminance = dot(baseColor.rgb, vec3(0.299, 0.587, 0.114));
    vec3 vibrantColor = mix(vec3(luminance), baseColor.rgb, 1.4); // 40% Color Boost
    vibrantColor = (vibrantColor - 0.5) * 1.15 + 0.5; // 15% Contrast Boost

    // 3. Smart Atmospheric Fog (Zero Lag)
    // Ye real 3D fog nahi hai, bas dur ki cheezon par ek dhundla blue effect dalega
    float fogDepth = smoothstep(0.35, 1.0, v_texcoord.y); 
    vec3 fogColor = vec3(0.55, 0.65, 0.75); // Realistic Sky-Blue Fog tint
    
    // Mix the fog with our vibrant game color
    vec3 finalColor = mix(vibrantColor, fogColor, fogDepth * 0.25); // 25% max fog intensity

    // 4. Cinematic Vignette (Corners ko halka dark karna)
    // Isse ekdum professional gaming monitor wali feel aati hai
    float dist = distance(v_texcoord, vec2(0.5));
    finalColor *= smoothstep(0.85, 0.25, dist);

    // Final Output dena screen par
    gl_FragColor = vec4(finalColor, baseColor.a);
}
