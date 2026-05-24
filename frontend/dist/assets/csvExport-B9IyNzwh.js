function a(r,t){if(!t||!t.length)return;const o=Object.keys(t[0]),l=c=>{const e=c==null?"":String(c);return e.includes(",")||e.includes('"')||e.includes(`
`)||e.includes("\r")?`"${e.replace(/"/g,'""')}"`:e},d=[o.map(l).join(","),...t.map(c=>o.map(e=>l(c[e])).join(","))],i="\uFEFF",u=new Blob([i+d.join(`
`)],{type:"text/csv;charset=utf-8;"}),s=URL.createObjectURL(u),n=document.createElement("a");n.href=s,n.download=r,document.body.appendChild(n),n.click(),document.body.removeChild(n),URL.revokeObjectURL(s)}export{a as e};
