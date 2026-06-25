declare module 'bpmn-js/lib/Modeler' {
  export default class BpmnModeler {
    constructor(options: any);
    importXML(xml: string): Promise<{ warnings: any[] }>;
    saveXML(options?: { format?: boolean }): Promise<{ xml: string }>;
    saveSVG(): Promise<{ svg: string }>;
    destroy(): void;
    get(name: string): any;
  }
}
