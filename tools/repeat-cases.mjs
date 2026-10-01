// Cross-check for the repeat rules: prints {items, exp}. Paste ha/repeating-tasks.jinja into
// ha_eval_template with variables {lists: ["L"], r: {L: {items}}, fixed_now: "2026-10-01T10:00:00+01:00"}
// and compare its "set" due times with exp (case 16 uses another "now": leave it out there).
// Run in UK time: TZ=Europe/London node tools/repeat-cases.mjs
import { parseTask, afterDone, firstDue } from '../src/repeat.js';
const p=(n)=>String(n).padStart(2,'0');
const fmt=(x)=>x&&`${x.getFullYear()}-${p(x.getMonth()+1)}-${p(x.getDate())} ${p(x.getHours())}:${p(x.getMinutes())}:00`;
const isoOff=(x)=>{const o=-x.getTimezoneOffset();return `${x.getFullYear()}-${p(x.getMonth()+1)}-${p(x.getDate())}T${p(x.getHours())}:${p(x.getMinutes())}:00${o>=0?'+':'-'}${p(Math.floor(Math.abs(o)/60))}:${p(Math.abs(o)%60)}`};
const NOW=new Date('2026-10-01T10:00');
const C=[
 ['Every day 08:00','completed','2026-10-01T08:00'],
 ['Every 3 days 08:00','completed','2026-09-26T08:00'],
 ['Every 3 days 08:00','needs_action',null],
 ['Mon 09:00, Thu 18:30','completed','2026-09-28T09:00'],
 ['Mon 09:00, Thu 18:30','needs_action',null],
 ['Every 2 weeks: Mon 09:00, Thu 18:30','completed','2026-09-24T18:30'],
 ['Every 2 weeks: Mon 09:00, Thu 18:30','completed','2026-09-17T18:30'],
 ['Monthly on the 31st 10:00','completed','2026-08-31T10:00'],
 ['Monthly on the last day 10:00','needs_action',null],
 ['Every 3 months on the 15th 10:00','completed','2026-07-15T10:00'],
 ['Yearly on 29 Feb 09:00','needs_action',null],
 ['Yearly on 12 Mar 09:00','completed','2026-03-12T09:00'],
 ['Every 30 days after done 09:00','completed','2026-09-01T09:00'],
 ['Every month after done 09:00','completed',null],
 ['Every 2 weeks after done 20:00','needs_action',null],
 ['Every day after done 11:00','needs_action',null],
 ['Sun 09:00','completed','2026-10-18T09:00'],
 ['Once','completed','2026-09-30T09:00'],
 ['Just a note','completed',null],
];
const items=C.map(([d,s,due],i)=>({uid:'u'+i,summary:'T'+i,status:s,description:d+' · for Jamie',...(due?{due:isoOff(new Date(due))}:{})}));
const exp=C.map(([d,s,due],i)=>{const t=parseTask(d+' · for Jamie');let o=null;
 if(t.repeat&&t.repeat.type!=='once'){ const now = i===16? new Date('2026-10-18T10:00'):NOW;
   if(s==='completed') o=afterDone(t.repeat,due?new Date(due):null,now); else if(!due) o=firstDue(t.repeat,now,now);}
 return fmt(o)});
console.log(JSON.stringify({items, exp}));
