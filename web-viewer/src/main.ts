// This script simulates FPS updates and reads image resolution

const img = document.getElementById("frame-view") as HTMLImageElement;
const fpsLabel = document.getElementById("fps")!;
const resLabel = document.getElementById("resolution")!;

// update resolution when image loads
img.onload = () => {
    resLabel.textContent = `${img.naturalWidth} x ${img.naturalHeight}`;
};

// fake FPS animation for demonstration
let counter = 1;
setInterval(() => {
    fpsLabel.textContent = (20 + Math.floor(Math.random() * 10)).toString();
}, 500);
