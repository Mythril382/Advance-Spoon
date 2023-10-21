package lights.graphics;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import lights.*;

public class AltLightBatch extends SpriteBatch{
    Seq<LightRequest> requests = new Seq<>(maxRequests);
    FloatSeq uncapture = new FloatSeq();
    int calls = 0;
    boolean flushing;
    boolean auto;

    boolean glow = false;
    boolean glowTexture = false;

    Batch lastBatch;

    final static int maxRequests = 2048;

    public AltLightBatch(){
        super(maxRequests, createShaderL());

        for(int i = 0; i < maxRequests; i++){
            requests.add(new LightRequest());
        }
    }

    public void setGlow(boolean glow){
        this.glow = glow;
    }

    public void begin(){
        lastBatch = Core.batch;
        Draw.flush();
        Mat proj = Draw.proj(), trans = Draw.trans();
        Core.batch = this;
        Draw.proj(proj);
        Draw.trans(trans);

        calls = 0;
    }
    public void end(){
        uncapture.clear();

        Draw.reset();
        Draw.flush();
        
        calls = 0;

        Core.batch = lastBatch;
    }

    public void setAuto(float z, boolean auto){
        LightRequest rq = obtain();
        rq.z = z;
        //rq.texture = null;
        rq.action = (byte)(1 | ((auto ? 1 : 0) << 1));
    }

    public void addUncapture(float lowZ, float highZ){
        uncapture.add(lowZ, highZ);
    }
    boolean invalid(){
        if(flushing) return false;
        int s = uncapture.size;
        float[] uc = uncapture.items;
        for(int i = 0; i < s; i += 2){
            if(z >= uc[i] && z <= uc[i + 1]) return true;
        }

        return false;
    }

    @Override
    protected void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float rotation){
        if(flushing || calls >= maxRequests || invalid() || color.a < 0.7f) return;

        LightRequest rq = obtain();
        float[] vertices = rq.vertices;

        float color = (glow || AdvanceLighting.autoGlowRegions.contains(region)) ? this.colorPacked : Color.blackFloatBits;
        float mixColor = this.mixColorPacked;

        rq.texture = region.texture;
        rq.color = colorPacked;
        rq.mixColor = mixColorPacked;
        rq.z = z;

        if(!Mathf.zero(rotation)){
            float worldOriginX = x + originX;
            float worldOriginY = y + originY;
            float fx = -originX;
            float fy = -originY;
            float fx2 = width - originX;
            float fy2 = height - originY;

            float cos = Mathf.cosDeg(rotation);
            float sin = Mathf.sinDeg(rotation);

            float x1 = cos * fx - sin * fy + worldOriginX;
            float y1 = sin * fx + cos * fy + worldOriginY;
            float x2 = cos * fx - sin * fy2 + worldOriginX;
            float y2 = sin * fx + cos * fy2 + worldOriginY;
            float x3 = cos * fx2 - sin * fy2 + worldOriginX;
            float y3 = sin * fx2 + cos * fy2 + worldOriginY;
            float x4 = x1 + (x3 - x2);
            float y4 = y3 - (y2 - y1);

            float u = region.u;
            float v = region.v2;
            float u2 = region.u2;
            float v2 = region.v;

            vertices[0] = x1;
            vertices[1] = y1;
            vertices[2] = color;
            vertices[3] = u;
            vertices[4] = v;
            vertices[5] = mixColor;

            vertices[6] = x2;
            vertices[7] = y2;
            vertices[8] = color;
            vertices[9] = u;
            vertices[10] = v2;
            vertices[11] = mixColor;

            vertices[12] = x3;
            vertices[13] = y3;
            vertices[14] = color;
            vertices[15] = u2;
            vertices[16] = v2;
            vertices[17] = mixColor;

            vertices[18] = x4;
            vertices[19] = y4;
            vertices[20] = color;
            vertices[21] = u2;
            vertices[22] = v;
            vertices[23] = mixColor;
        }else{
            float fx2 = x + width;
            float fy2 = y + height;
            float u = region.u;
            float v = region.v2;
            float u2 = region.u2;
            float v2 = region.v;

            vertices[0] = x;
            vertices[1] = y;
            vertices[2] = color;
            vertices[3] = u;
            vertices[4] = v;
            vertices[5] = mixColor;

            vertices[6] = x;
            vertices[7] = fy2;
            vertices[8] = color;
            vertices[9] = u;
            vertices[10] = v2;
            vertices[11] = mixColor;

            vertices[12] = fx2;
            vertices[13] = fy2;
            vertices[14] = color;
            vertices[15] = u2;
            vertices[16] = v2;
            vertices[17] = mixColor;

            vertices[18] = fx2;
            vertices[19] = y;
            vertices[20] = color;
            vertices[21] = u2;
            vertices[22] = v;
            vertices[23] = mixColor;
        }

        //Pain and Misery
        if(!glowTexture){
            glowTexture = true;
            TextureRegion gc;
            boolean lg = glow;
            glow = true;
            if((gc = AdvanceLighting.glowEquiv.get(region)) != null){
                draw(gc, x, y, originX, originY, width, height, rotation);
            }
            glow = lg;
            glowTexture = false;
        }
    }
    
    void superDraw(Texture texture, float[] spriteVertices, int offset, int count){
        super.draw(texture, spriteVertices, offset, count);
    }

    @Override
    protected void draw(Texture texture, float[] spriteVertices, int offset, int count){
        /*
        if(flushing){
            super.draw(texture, spriteVertices, offset, count);
            return;
        }
        */
        if(flushing || calls >= maxRequests || invalid() || color.a < 0.7f) return;

        LightRequest rq = obtain();
        rq.texture = texture;
        rq.color = colorPacked;
        rq.mixColor = mixColorPacked;
        rq.z = z;
        float[] vertices = rq.vertices;

        //System.arraycopy(spriteVertices, 0, rq.vertices, 0, 24);
        System.arraycopy(spriteVertices, offset, rq.vertices, 0, 24);

        /*
        if(!glow){
            float black = Color.blackFloatBits;
            for(int i = 3; i < 24; i += 6){
                vertices[i] = black;
            }
        }
         */
        float color = glow ? colorPacked : Color.blackFloatBits;
        for(int i = 2; i < 24; i += 6){
            vertices[i] = color;
        }
    }

    @Override
    protected void draw(Runnable request){
        if(flushing) return;
        LightRequest r = obtain();
        r.run = request;
        r.z = z;
    }

    @Override
    protected void flush(){
        if(!flushing){
            flushing = true;
            int size = requests.size;
            requests.size = calls;
            requests.sort();

            for(LightRequest r : requests){
                if(r.texture != null){
                    if(auto) r.convertAutoColor();
                    superDraw(r.texture, r.vertices, 0, 24);
                }else if(r.run != null){
                    r.run.run();
                }else{
                    byte action = r.action;
                    if(action != 0) auto = action != 1;
                }
            }
            requests.size = size;
            super.flush();

            flushing = false;
        }else{
            super.flush();
        }
    }

    @Override
    protected void setShader(Shader shader, boolean apply){
        //
    }

    @Override
    protected void setBlending(Blending blending){
        glow = blending == Blending.additive;
    }

    LightRequest obtain(){
        if(calls >= requests.size){
            LightRequest r = new LightRequest();
            requests.add(r);
            calls++;
            return r;
        }
        //calls++;
        //requests.size = calls;
        LightRequest r = requests.get(calls);
        r.texture = null;
        r.run = null;
        r.action = 0;
        r.z = 0f;
        calls++;
        return r;
    }

    static Shader createShaderL(){
        return new Shader("""
                attribute vec4 a_position;
                attribute vec4 a_color;
                attribute vec2 a_texCoord0;
                attribute vec4 a_mix_color;
                uniform mat4 u_projTrans;
                varying vec4 v_color;
                varying vec4 v_mix_color;
                varying vec2 v_texCoords;
                
                void main(){
                    v_color = a_color;
                    v_color.a = v_color.a * (255.0/254.0);
                    v_mix_color = a_mix_color;
                    v_mix_color.a *= (255.0/254.0);
                    v_texCoords = a_texCoord0;
                    gl_Position = u_projTrans * a_position;
                }
                """, """
                varying lowp vec4 v_color;
                varying lowp vec4 v_mix_color;
                varying vec2 v_texCoords;
                uniform sampler2D u_texture;
                                
                void main(){
                    vec4 c = texture2D(u_texture, v_texCoords);
                
                    float alpha = ((c.a * v_color.a) - 0.7) / (1.0 - 0.7);
                    gl_FragColor = vec4(mix(c.rgb, v_mix_color.rgb, v_mix_color.a) * v_color.rgb, alpha);
                }
                """);
    }
}